import asyncio
import logging
from decimal import Decimal
from elasticsearch import AsyncElasticsearch

from app.models.product import Product
from app.core.config import settings

logger = logging.getLogger(__name__)

# Module-level, shared async Elasticsearch client instance
es_client = AsyncElasticsearch(hosts=[settings.elasticsearch_url])

PRODUCTS_INDEX = "products"

async def create_products_index(max_retries: int = 10, base_delay: float = 2.0, max_delay: float = 15.0) -> None:
    """
    Ensures the Elasticsearch 'products' index and mapping exist on startup,
    employing an exponential backoff retry loop to wait for Elasticsearch to become ready.
    """
    for attempt in range(1, max_retries + 1):
        try:
            logger.info(f"Connecting to Elasticsearch at {settings.elasticsearch_url} (attempt {attempt}/{max_retries})...")
            exists = await es_client.indices.exists(index=PRODUCTS_INDEX)
            if not exists:
                await es_client.indices.create(
                    index=PRODUCTS_INDEX,
                    mappings={
                        "properties": {
                            "name": {"type": "text"},
                            "description": {"type": "text"},
                            "category_ids": {"type": "keyword"},
                            "price": {"type": "scaled_float", "scaling_factor": 100},
                            "stock_quantity": {"type": "integer"},
                        }
                    },
                )
                logger.info(f"Successfully created Elasticsearch index '{PRODUCTS_INDEX}'")
            else:
                logger.info(f"Elasticsearch index '{PRODUCTS_INDEX}' already exists and is ready.")
            return
        except Exception as e:
            if attempt == max_retries:
                logger.error(f"Failed to connect to Elasticsearch after {max_retries} attempts: {e}")
                raise e
            delay = min(base_delay * (2 ** (attempt - 1)), max_delay)
            logger.warning(
                f"Elasticsearch not ready yet ({type(e).__name__}: {e}). "
                f"Retrying in {delay:.1f}s (attempt {attempt}/{max_retries})..."
            )
            await asyncio.sleep(delay)

async def index_product(product: Product) -> None:
    """
    Converts a MongoDB-backed Product document into the search-optimized Elasticsearch shape.
    """
    document = {
        "name": product.name,
        "description": product.description or "",
        "price": round(float(product.price), 2),
        "stock_quantity": product.stock_quantity,
        "category_ids": [str(link.ref.id) for link in product.categories],
    }

    await es_client.index(
        index=PRODUCTS_INDEX,
        id=str(product.id),
        document=document,
    )

async def remove_product_from_index(product_id: str) -> None:
    """
    Removes a product from the search index when deleted from MongoDB.
    """
    try:
        await es_client.delete(index=PRODUCTS_INDEX, id=product_id)
    except Exception:
        pass

async def search_products(
    query: str | None = None,
    min_price: Decimal | None = None,
    max_price: Decimal | None = None,
    category_id: str | None = None,
    in_stock_only: bool = False,
) -> list[dict]:
    """
    Executes a structured search query against Elasticsearch.
    """
    must_clauses = []
    filter_clauses = []

    if query:
        must_clauses.append({
            "multi_match": {
                "query": query,
                "fields": ["name^2", "description"],
            }
        })
    else:
        must_clauses.append({"match_all": {}})

    if min_price is not None or max_price is not None:
        price_range = {}
        if min_price is not None:
            price_range["gte"] = float(min_price)
        if max_price is not None:
            price_range["lte"] = float(max_price)
        filter_clauses.append({"range": {"price": price_range}})

    if category_id:
        filter_clauses.append({"term": {"category_ids": category_id}})

    if in_stock_only:
        filter_clauses.append({"range": {"stock_quantity": {"gt": 0}}})

    response = await es_client.search(
        index=PRODUCTS_INDEX,
        query={
            "bool": {
                "must": must_clauses,
                "filter": filter_clauses,
            }
        },
    )

    results = []
    for hit in response["hits"]["hits"]:
        source = hit["_source"]
        results.append({
            "id": hit["_id"],
            "name": source["name"],
            "description": source["description"],
            "price": Decimal(str(source["price"])),
            "stock_quantity": source["stock_quantity"],
        })

    return results
