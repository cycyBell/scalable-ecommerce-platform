import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from app.services.reconciliation import reconcile_products_index, BATCH_SIZE


@pytest.mark.asyncio
async def test_reconcile_products_index_indexes_missing_product():
    mock_product = MagicMock()
    mock_product.id = "60d5ec49f1a2c81234567890"

    mock_find_all = MagicMock()
    mock_find_all.skip.return_value.limit.return_value.to_list = AsyncMock(
        side_effect=[[mock_product], []]
    )

    mock_es_search = AsyncMock(return_value={"hits": {"hits": []}})

    with patch("app.models.product.Product.find_all", return_value=mock_find_all), \
         patch("app.services.reconciliation.es_client.search", mock_es_search), \
         patch("app.services.reconciliation.index_product", new_callable=AsyncMock) as mock_index:

        await reconcile_products_index()

        mock_index.assert_called_once_with(mock_product)


@pytest.mark.asyncio
async def test_reconcile_products_index_removes_orphaned_es_item():
    mock_find_all = MagicMock()
    mock_find_all.skip.return_value.limit.return_value.to_list = AsyncMock(return_value=[])

    mock_es_search = AsyncMock(
        return_value={
            "hits": {
                "hits": [
                    {"_id": "orphaned_id_123", "sort": ["orphaned_id_123"]}
                ]
            }
        }
    )
    # Second call for search_after scan returns empty list to stop loop
    mock_es_search.side_effect = [
        {"hits": {"hits": [{"_id": "orphaned_id_123", "sort": ["orphaned_id_123"]}]}},
        {"hits": {"hits": []}}
    ]

    with patch("app.models.product.Product.find_all", return_value=mock_find_all), \
         patch("app.models.product.Product.get", new_callable=AsyncMock) as mock_get, \
         patch("app.services.reconciliation.es_client.search", mock_es_search), \
         patch("app.services.reconciliation.remove_product_from_index", new_callable=AsyncMock) as mock_remove:

        mock_get.return_value = None

        await reconcile_products_index()

        mock_get.assert_called_once_with("orphaned_id_123")
        mock_remove.assert_called_once_with("orphaned_id_123")
