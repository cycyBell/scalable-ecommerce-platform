import asyncio
import logging
from pymongo.errors import PyMongoError

from app.models.product import Product
from app.db.elasticsearch import index_product, remove_product_from_index
from app.db.redis import invalidate_product_cache

logger = logging.getLogger(__name__)


async def handle_product_change_event(change: dict) -> None:
    """
    Processes a single MongoDB Change Stream event for the products collection.
    Asynchronously updates Elasticsearch indices and invalidates Redis cache.
    """
    op_type = change.get("operationType")
    document_key = change.get("documentKey", {})
    product_id = str(document_key.get("_id")) if "_id" in document_key else None

    if not product_id:
        logger.warning(f"Received change event without _id: {change}")
        return

    if op_type in ("insert", "update", "replace"):
        logger.info(f"Change stream: handling {op_type} for product {product_id}")
        product = await Product.get(product_id)
        if product:
            try:
                await index_product(product)
            except Exception as e:
                logger.error(f"Failed to index product {product_id} into ES via change stream: {e}")
        else:
            # If deleted immediately after insertion/update
            try:
                await remove_product_from_index(product_id)
            except Exception:
                pass

        try:
            await invalidate_product_cache(product_id)
        except Exception as e:
            logger.error(f"Failed to invalidate cache for product {product_id}: {e}")

    elif op_type == "delete":
        logger.info(f"Change stream: handling delete for product {product_id}")
        try:
            await remove_product_from_index(product_id)
        except Exception as e:
            logger.error(f"Failed to remove product {product_id} from ES via change stream: {e}")

        try:
            await invalidate_product_cache(product_id)
        except Exception as e:
            logger.error(f"Failed to invalidate cache for product {product_id}: {e}")


async def start_change_stream_listener() -> None:
    """
    Runs continuous background loop watching MongoDB Change Stream on the products collection.
    Automatically handles reconnection upon transient network/database errors.
    """
    logger.info("Starting MongoDB Change Stream listener worker...")
    while True:
        try:
            collection = Product.get_pymongo_collection()
            async with collection.watch(full_document="updateLookup") as stream:

                async for change in stream:
                    await handle_product_change_event(change)
        except asyncio.CancelledError:
            logger.info("MongoDB Change Stream listener task cancelled cleanly.")
            break
        except PyMongoError as e:
            logger.warning(f"MongoDB Change Stream encountered PyMongo error: {e}. Retrying in 3s...")
            await asyncio.sleep(3)
        except Exception as e:
            logger.error(f"Unexpected error in MongoDB Change Stream listener: {e}. Retrying in 5s...")
            await asyncio.sleep(5)
