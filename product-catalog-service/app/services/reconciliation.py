import asyncio
import logging

from app.db.elasticsearch import index_product, remove_product_from_index, es_client, PRODUCTS_INDEX
from app.models.product import Product

logger = logging.getLogger(__name__)


async def reconcile_products_index() -> None:
    """
    Compares MongoDB's actual product set against what's indexed in
    Elasticsearch, and corrects any drift found. This is our safety
    net against the dual-write problem described above - it doesn't
    prevent drift from happening, but it guarantees drift never
    persists for longer than one reconciliation interval.
    """

    # Fetch every product id currently in MongoDB - the source of
    # truth. .to_list() materializes the async cursor into a real
    # Python list we can work with directly.
    mongo_products = await Product.find_all().to_list()
    mongo_ids = {str(p.id) for p in mongo_products}

    # Fetch every document id currently in the Elasticsearch index.
    # match_all with a large size pulls everything - fine for a
    # portfolio-scale catalog; a genuinely huge catalog would need this
    # paginated via Elasticsearch's scroll/search_after APIs instead,
    # worth flagging as a known scaling limit of this simple approach.
    es_response = await es_client.search(
        index=PRODUCTS_INDEX,
        query={"match_all": {}},
        size=10000,
        _source=False,  # we only need the ids here, not full documents
    )
    es_ids = {hit["_id"] for hit in es_response["hits"]["hits"]}

    # Anything in MongoDB but NOT in Elasticsearch: missing, needs
    # indexing. This covers both "never got indexed due to a crash
    # between the two writes" and "was indexed once, then Elasticsearch
    # lost it somehow (e.g. index recreated)."
    missing_from_es = mongo_ids - es_ids
    for product in mongo_products:
        if str(product.id) in missing_from_es:
            await index_product(product)
            logger.info(f"Reconciliation: re-indexed missing product {product.id}")

    # Anything in Elasticsearch but NOT in MongoDB: a leftover from a
    # deletion where the MongoDB delete succeeded but the Elasticsearch
    # delete call never completed.
    orphaned_in_es = es_ids - mongo_ids
    for product_id in orphaned_in_es:
        await remove_product_from_index(product_id)
        logger.info(f"Reconciliation: removed orphaned product {product_id} from index")

    if missing_from_es or orphaned_in_es:
        logger.info(
            f"Reconciliation complete: {len(missing_from_es)} re-indexed, "
            f"{len(orphaned_in_es)} removed"
        )


async def reconciliation_loop(interval_seconds: int = 300) -> None:
    """
    Runs reconcile_products_index() repeatedly, forever, sleeping
    between runs. This is intentionally simple - a real production
    system at larger scale might use a proper job scheduler (e.g.
    Celery Beat, or an external cron-triggered call), but a plain
    asyncio loop is a perfectly legitimate, honest choice for a
    single-instance service at this scale.
    """
    while True:
        try:
            await reconcile_products_index()
        except Exception:
            # A reconciliation failure (e.g. Elasticsearch briefly
            # unreachable) should NEVER crash the whole application -
            # log it and simply try again on the next interval. This
            # is a deliberate resilience choice: the reconciliation
            # task's job is to IMPROVE consistency over time, not to be
            # a single point of failure for the entire service.
            logger.exception("Reconciliation run failed")

        await asyncio.sleep(interval_seconds)