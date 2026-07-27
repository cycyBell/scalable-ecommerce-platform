import os

# Set dummy environment variables BEFORE importing app code
os.environ.setdefault("MONGO_USERNAME", "mongoadmin")
os.environ.setdefault("MONGO_PASSWORD", "mongoadmin")
os.environ.setdefault("MONGO_HOST", "localhost")
os.environ.setdefault("MONGO_PORT", "27017")
os.environ.setdefault("MONGO_DB_NAME", "test_db")

import pytest_asyncio
from beanie import init_beanie
from httpx import ASGITransport, AsyncClient
from mongomock_motor import AsyncMongoMockClient
from motor.motor_asyncio import AsyncIOMotorClient
# Updated import to fix deprecation warning
from testcontainers.community.mongodb import MongoDbContainer

from app.main import app
from app.models.category import Category
from app.models.product import Product


"""
Integration tests for the Products API.

NOTE: These tests use a real, disposable MongoDB via Testcontainers for
the primary database - this is what matters most, since it's what lets
us genuinely verify the atomic stock-adjustment logic against a real
database engine, not a mock.

However, Redis and Elasticsearch are NOT mocked or containerized here -
these tests rely on the local development stack's catalog-redis and
catalog-search containers already being up (e.g. via
`docker compose up -d`). This is a deliberate, scoped choice: Redis/ES
involvement here is incidental to what these specific tests are
verifying, so the added complexity/runtime of Testcontainers for those
two wasn't judged worth it for this test suite.
"""


# -------------------------------------------------------------------
# Fast In-Memory Initialization for Unit Tests
# -------------------------------------------------------------------
@pytest_asyncio.fixture(autouse=True)
async def init_unit_test_beanie():
    """
    Runs automatically before every test. Initializes Beanie in-memory 
    so unit tests can instantiate and inspect models without Docker.
    """
    mock_client = AsyncMongoMockClient()
    await init_beanie(
        database=mock_client.get_database("unit_test_db"),
        document_models=[Product, Category],
    )


# -------------------------------------------------------------------
# Docker Container Setup for Integration Tests
# -------------------------------------------------------------------
@pytest_asyncio.fixture
async def mongo_container():
    with MongoDbContainer("mongo:7") as mongo:
        yield mongo


@pytest_asyncio.fixture
async def test_client(mongo_container):
    """
    Integration test fixture. Re-initializes Beanie against a real,
    ephemeral MongoDB Docker container.
    """
    connection_url = mongo_container.get_connection_url()
    client = AsyncIOMotorClient(connection_url)
    await init_beanie(
        database=client["test_catalog_db"],
        document_models=[Product, Category],
    )

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac

    client.close()