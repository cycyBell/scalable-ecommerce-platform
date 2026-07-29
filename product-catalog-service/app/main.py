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
@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    await create_products_index()


     # asyncio.create_task schedules reconciliation_loop to run
    # CONCURRENTLY with the rest of the app, rather than blocking
    # startup waiting for it (which would be wrong - it's designed to
    # run forever, so "awaiting" it directly here would mean the app
    # never finishes starting up at all).
    reconciliation_task = asyncio.create_task(reconciliation_loop())
    yield
    # Code after "yield" runs on shutdown. Cancelling the task here
    # ensures a clean shutdown - without this, the background loop
    # would keep trying to run against connections that are being torn
    # down, producing confusing errors in your shutdown logs.
    reconciliation_task.cancel()


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





