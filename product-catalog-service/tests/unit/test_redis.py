import pytest
from unittest.mock import AsyncMock, patch
from app.core.config import Settings
from app.db.redis import get_cached_product, cache_product, invalidate_product_cache


def test_redis_url_formatting_with_password():
    custom_settings = Settings(
        redis_host="localhost",
        redis_port=6379,
        redis_password="secret_password_123",
        mongo_username="admin",
        mongo_password="pass",
    )
    assert custom_settings.redis_url == "redis://:secret_password_123@localhost:6379"


def test_redis_url_formatting_without_password():
    custom_settings = Settings(
        redis_host="localhost",
        redis_port=6379,
        redis_password="",
        mongo_username="admin",
        mongo_password="pass",
    )
    assert custom_settings.redis_url == "redis://localhost:6379"


@pytest.mark.asyncio
async def test_get_cached_product_returns_deserialized_json():
    mock_redis = AsyncMock()
    mock_redis.get.return_value = '{"id": "123", "name": "Test Product", "price": "19.99"}'

    with patch("app.db.redis.redis_client", mock_redis):
        result = await get_cached_product("123")
        assert result == {"id": "123", "name": "Test Product", "price": "19.99"}
        mock_redis.get.assert_called_once_with("product:123")


@pytest.mark.asyncio
async def test_invalidate_product_cache_deletes_key():
    mock_redis = AsyncMock()

    with patch("app.db.redis.redis_client", mock_redis):
        await invalidate_product_cache("123")
        mock_redis.delete.assert_called_once_with("product:123")
