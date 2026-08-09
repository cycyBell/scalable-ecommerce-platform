import asyncio
import logging
import json
from decimal import Decimal
from redis.asyncio import Redis

from app.core.config import settings

logger = logging.getLogger(__name__)

# Async Redis client instance
redis_client = Redis.from_url(settings.redis_url, decode_responses=True)

PRODUCT_CACHE_TTL_SECONDS = 300  # 5 minutes cache TTL

def _product_cache_key(product_id: str) -> str:
    return f"product:{product_id}"

async def init_redis(max_retries: int = 10, base_delay: float = 2.0, max_delay: float = 15.0) -> None:
    """
    Verifies Redis connectivity on startup with an exponential backoff retry loop.
    """
    for attempt in range(1, max_retries + 1):
        try:
            logger.info(f"Connecting to Redis at {settings.redis_host}:{settings.redis_port} (attempt {attempt}/{max_retries})...")
            await redis_client.ping()
            logger.info("Successfully connected to Redis cache store.")
            return
        except Exception as e:
            if attempt == max_retries:
                logger.error(f"Failed to connect to Redis after {max_retries} attempts: {e}")
                raise e
            delay = min(base_delay * (2 ** (attempt - 1)), max_delay)
            logger.warning(
                f"Redis not ready yet ({type(e).__name__}: {e}). "
                f"Retrying in {delay:.1f}s (attempt {attempt}/{max_retries})..."
            )
            await asyncio.sleep(delay)

async def get_cached_product(product_id: str) -> dict | None:
    cached = await redis_client.get(_product_cache_key(product_id))
    if cached is None:
        return None
    return json.loads(cached)

async def cache_product(product_id: str, product_data: dict) -> None:
    serializable = {**product_data, "price": str(product_data["price"])}
    await redis_client.set(
        _product_cache_key(product_id),
        json.dumps(serializable),
        ex=PRODUCT_CACHE_TTL_SECONDS,
    )

async def invalidate_product_cache(product_id: str) -> None:
    await redis_client.delete(_product_cache_key(product_id))