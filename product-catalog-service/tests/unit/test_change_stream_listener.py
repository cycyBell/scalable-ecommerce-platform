import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from app.services.change_stream_listener import handle_product_change_event


@pytest.mark.asyncio
async def test_handle_insert_event():
    mock_product = MagicMock()
    mock_product.id = "60d5ec49f1a2c81234567890"

    change_event = {
        "operationType": "insert",
        "documentKey": {"_id": "60d5ec49f1a2c81234567890"},
    }

    with patch("app.models.product.Product.get", new_callable=AsyncMock) as mock_get, \
         patch("app.services.change_stream_listener.index_product", new_callable=AsyncMock) as mock_index, \
         patch("app.services.change_stream_listener.invalidate_product_cache", new_callable=AsyncMock) as mock_invalidate:

        mock_get.return_value = mock_product

        await handle_product_change_event(change_event)

        mock_get.assert_called_once_with("60d5ec49f1a2c81234567890")
        mock_index.assert_called_once_with(mock_product)
        mock_invalidate.assert_called_once_with("60d5ec49f1a2c81234567890")


@pytest.mark.asyncio
async def test_handle_delete_event():
    change_event = {
        "operationType": "delete",
        "documentKey": {"_id": "60d5ec49f1a2c81234567890"},
    }

    with patch("app.services.change_stream_listener.remove_product_from_index", new_callable=AsyncMock) as mock_remove, \
         patch("app.services.change_stream_listener.invalidate_product_cache", new_callable=AsyncMock) as mock_invalidate:

        await handle_product_change_event(change_event)

        mock_remove.assert_called_once_with("60d5ec49f1a2c81234567890")
        mock_invalidate.assert_called_once_with("60d5ec49f1a2c81234567890")
