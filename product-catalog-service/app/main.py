from contextlib import asynccontextmanager
import asyncio
import logging

from fastapi import FastAPI

from app.db.mongodb import init_db
from app.db.redis import init_redis
from app.db.elasticsearch import create_products_index
from app.services.reconciliation import reconciliation_loop
from app.services.change_stream_listener import start_change_stream_listener
from app.routers import categories, products

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing Product Catalog Service backing datastores...")
    
    # Resilient initialization with exponential backoff
    await init_db()
    await init_redis()
    await create_products_index()

    logger.info("Backing datastores connected. Starting background tasks...")
    reconciliation_task = asyncio.create_task(reconciliation_loop())
    change_stream_task = asyncio.create_task(start_change_stream_listener())
    
    logger.info("Product Catalog Service is ready and listening for incoming HTTP traffic.")
    yield
    
    logger.info("Shutting down background tasks...")
    reconciliation_task.cancel()
    change_stream_task.cancel()

app = FastAPI(
    title="Product Catalog Service",
    description="Manages product listings, categories, and inventory",
    version="0.0.1",
    lifespan=lifespan,
)

app.include_router(categories.router)
app.include_router(products.router)

@app.get("/health", status_code=200)
async def health_check():
    return {
        "status": "healthy",
        "service": "product-catalog-service"
    }
