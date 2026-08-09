import asyncio
import logging
from motor.motor_asyncio import AsyncIOMotorClient
from beanie import init_beanie

from app.core.config import settings
from app.models.product import Product
from app.models.category import Category

logger = logging.getLogger(__name__)

# A module-level client with internal connection pooling
client = AsyncIOMotorClient(settings.mongo_uri)

async def init_db(max_retries: int = 10, base_delay: float = 2.0, max_delay: float = 15.0) -> None:
    """
    Initializes Beanie ODM and verifies MongoDB replica set connectivity on startup,
    employing an exponential backoff retry loop to survive transient database startup delays.
    """
    for attempt in range(1, max_retries + 1):
        try:
            logger.info(f"Connecting to MongoDB at {settings.mongo_host}:{settings.mongo_port} (attempt {attempt}/{max_retries})...")
            # Verify connectivity by running a ping command on admin database
            await client.admin.command('ping')
            database = client[settings.mongo_db]
            await init_beanie(
                database=database,
                document_models=[Product, Category],
            )
            logger.info("Successfully connected to MongoDB and initialized Beanie ODM.")
            return
        except Exception as e:
            if attempt == max_retries:
                logger.error(f"Failed to initialize MongoDB after {max_retries} attempts: {e}")
                raise e
            delay = min(base_delay * (2 ** (attempt - 1)), max_delay)
            logger.warning(
                f"MongoDB not ready yet ({type(e).__name__}: {e}). "
                f"Retrying in {delay:.1f}s (attempt {attempt}/{max_retries})..."
            )
            await asyncio.sleep(delay)