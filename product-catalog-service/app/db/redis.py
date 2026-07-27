from redis.asyncio import Redis
import json
from decimal import Decimal

from app.core.config import settings

# redis.asyncio gives us an async-native client - matching Motor and
# the Elasticsearch async client we're already using elsewhere in this
# service, keeping the whole app consistently async rather than
# accidentally mixing blocking and non-blocking database calls.
redis_client = Redis.from_url(settings.redis_url, decode_responses=True)

# decode_responses=True means the client automatically decodes bytes
# back into Python strings for us - without this, every value we read
# back would be a raw bytes object we'd have to manually .decode()
# ourselves every time.

PRODUCT_CACHE_TTL_SECONDS = 300  # 5 minutes - short enough that even
                                  # if our cache-invalidation logic
                                  # ever missed a case, staleness
                                  # self-heals fairly quickly on its own

def _product_cache_key(product_id: str) -> str:
    return f"product:{product_id}"


async def get_cached_product(product_id: str) -> dict | None:
    cached = await redis_client.get(_product_cache_key(product_id))
    if cached is None:
        return None
    return json.loads(cached)


async def cache_product(product_id: str, product_data: dict) -> None:
    # json.dumps can't natively serialize Decimal - we convert it to a
    # plain string first (same "go through str() to stay exact"
    # principle we used earlier for Elasticsearch's price field) so we
    # never introduce float imprecision just for the sake of caching.
    serializable = {**product_data, "price": str(product_data["price"])}
    await redis_client.set(
        _product_cache_key(product_id),
        json.dumps(serializable),
        ex=PRODUCT_CACHE_TTL_SECONDS,
    )


async def invalidate_product_cache(product_id: str) -> None:
    # This is the OTHER half of cache-aside, and it's the half that's
    # easy to forget: every write path (update, delete, stock
    # adjustment) MUST call this, or a stale cached value could persist
    # and be served to users for up to PRODUCT_CACHE_TTL_SECONDS after
    # the real data has already changed underneath it.
    await redis_client.delete(_product_cache_key(product_id))