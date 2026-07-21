from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.db.mongodb import init_db
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
    yield


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




@app.get("/health")
def health_check():
    return {"status": "UP"}



