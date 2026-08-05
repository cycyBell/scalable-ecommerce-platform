from contextlib import asynccontextmanager
import asyncio

from fastapi import FastAPI

from app.db.mongodb import init_db
from app.services.reconciliation import reconciliation_loop
from app.db.elasticsearch import create_products_index
from app.models.product import Product

from app.routers import categories, products




# @asynccontextmanager + this "lifespan" pattern is FastAPI's modern,
# recommended way to run startup/shutdown logic — the direct equivalent
# of what a Spring Boot @PostConstruct method, or a CommandLineRunner
# bean, would do: "run this once, when the application starts."
#
# Code BEFORE the "yield" runs on startup. Code AFTER "yield" (none here
# yet) would run on shutdown — e.g. closing connections cleanly.
from app.services.change_stream_listener import start_change_stream_listener

@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await create_products_index()

    reconciliation_task = asyncio.create_task(reconciliation_loop())
    change_stream_task = asyncio.create_task(start_change_stream_listener())
    yield
    reconciliation_task.cancel()
    change_stream_task.cancel()



app = FastAPI(
    title="Product Catalog Service",
    description="Manages product listings, categories, and inventory",
    version="0.0.1",
    lifespan=lifespan,
)

from decimal import Decimal
from app.models.product import Product



app.include_router(categories.router)
app.include_router(products.router)

@app.get("/health", status_code=200)
async def health_check():
    return {
        "status": "healthy",
        "service": "product-catalog-service"
    }





