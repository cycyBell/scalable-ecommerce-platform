import pytest


@pytest.mark.asyncio
async def test_create_and_get_product(test_client):
    # ACT: a real HTTP POST, through the actual FastAPI app, into a
    # real (disposable) MongoDB instance.
    create_response = await test_client.post(
        "/products",
        json={"name": "Integration Test Widget", "price": "9.99", "stock_quantity": 5},
    )

    # ASSERT: this exercises validation, Product.from_price, the
    # MongoDB insert, and the response serialization all at once - a
    # genuine end-to-end proof, not an isolated unit.
    assert create_response.status_code == 201
    product_id = create_response.json()["id"]

    get_response = await test_client.get(f"/products/{product_id}")
    assert get_response.status_code == 200
    assert get_response.json()["price"] == "9.99"


@pytest.mark.asyncio
async def test_get_nonexistent_product_returns_404(test_client):
    response = await test_client.get("/products/000000000000000000000000")
    assert response.status_code == 404


@pytest.mark.asyncio
async def test_stock_adjustment_rejects_insufficient_stock(test_client):
    # ARRANGE: create a product with exactly 1 unit in stock.
    create_response = await test_client.post(
        "/products",
        json={"name": "Scarce Widget", "price": "5.00", "stock_quantity": 1},
    )
    product_id = create_response.json()["id"]

    # ACT: try to remove 2 units - more than available.
    response = await test_client.patch(
        f"/products/{product_id}/stock",
        json={"quantity_change": -2},
    )

    # ASSERT: this is the REAL proof the atomic MongoDB filter works -
    # unlike a unit test with a mock, this genuinely exercises
    # find_one_and_update against real MongoDB and confirms it
    # correctly refuses the operation.
    assert response.status_code == 409

    # Confirm stock genuinely wasn't touched.
    get_response = await test_client.get(f"/products/{product_id}")
    assert get_response.json()["stock_quantity"] == 1