from motor.motor_asyncio import AsyncIOMotorClient
from beanie import init_beanie

from app.core.config import settings
from app.models.product import Product
from app.models.category import Category


# A module-level client, created once. Motor's AsyncIOMotorClient
# manages a connection POOL internally (similar in spirit to HikariCP,
# which you saw in every one of User Service's startup logs) — we don't
# manually open/close a connection per request; Motor handles that
# efficiently behind the scenes.
client = AsyncIOMotorClient(settings.mongo_uri)


# This function does for MongoDB/Beanie what Flyway + Hibernate's
# schema validation did for us in User Service — except since MongoDB
# is schemaless, there's no migration to run. Instead, init_beanie's
# job is simpler: it tells Beanie "here are the Document classes
# (Product, Category) you're responsible for," so that calls like
# Product.find(), Product.insert(), etc. actually know which database
# and collection to talk to.
async def init_db():
    database = client[settings.mongo_db]
    await init_beanie(
        database=database,
        document_models=[Product, Category],
    )