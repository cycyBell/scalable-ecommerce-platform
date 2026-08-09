import asyncio
import logging

from app.db.elasticsearch import index_product, remove_product_from_index, es_client, PRODUCTS_INDEX
from app.models.product import Product

logger = logging.getLogger(__name__)

BATCH_SIZE = 500


async def reconcile_products_index() -> None:
    """
    Compares MongoDB's actual product set against Elasticsearch in batches,
    correcting any drift while maintaining a bounded memory footprint.
    """
    reindexed_count = 0
    removed_count = 0

    # 1. Batch scan MongoDB products and ensure they exist in Elasticsearch
    skip = 0
    while True:
        products_batch = await Product.find_all().skip(skip).limit(BATCH_SIZE).to_list()
        if not products_batch:
            break

        batch_ids = [str(p.id) for p in products_batch]

        try:
            es_response = await es_client.search(
                index=PRODUCTS_INDEX,
                query={"ids": {"values": batch_ids}},
                size=len(batch_ids),
                _source=False,
            )
            existing_es_ids = {hit["_id"] for hit in es_response["hits"]["hits"]}
        except Exception as e:
            logger.error(f"Reconciliation error querying ES batch: {e}")
            existing_es_ids = set()

        for product in products_batch:
            pid = str(product.id)
            if pid not in existing_es_ids:
                try:
                    await index_product(product)
                    reindexed_count += 1
                    logger.info(f"Reconciliation: re-indexed missing product {pid}")
                except Exception as e:
                    logger.error(f"Failed to re-index product {pid} during reconciliation: {e}")

        skip += len(products_batch)

    # 2. Batch scan Elasticsearch index using search_after to remove orphaned items
    search_after = None
    while True:
        search_kwargs = {
            "index": PRODUCTS_INDEX,
            "query": {"match_all": {}},
            "size": BATCH_SIZE,
            "sort": [{"_doc": "asc"}],
            "_source": False,
        }
        if search_after:
            search_kwargs["search_after"] = search_after

        try:
            es_batch_res = await es_client.search(**search_kwargs)
            hits = es_batch_res.get("hits", {}).get("hits", [])
            if not hits:
                break

            search_after = hits[-1]["sort"]

            for hit in hits:
                es_id = hit["_id"]
                product = await Product.get(es_id)
                if product is None:
                    try:
                        await remove_product_from_index(es_id)
                        removed_count += 1
                        logger.info(f"Reconciliation: removed orphaned product {es_id} from index")
                    except Exception as e:
                        logger.error(f"Failed to remove orphaned product {es_id}: {e}")

        except Exception as e:
            logger.error(f"Reconciliation error scanning ES index: {e}")
            break

    if reindexed_count > 0 or removed_count > 0:
        logger.info(
            f"Reconciliation complete: {reindexed_count} re-indexed, "
            f"{removed_count} removed"
        )


async def reconciliation_loop(interval_seconds: int = 300) -> None:
    """
    Runs reconcile_products_index() repeatedly in the background.
    """
    while True:
        try:
            await reconcile_products_index()
        except Exception:
            logger.exception("Reconciliation run failed")

        await asyncio.sleep(interval_seconds)