from decimal import Decimal

from app.models.product import Product


def test_from_price_converts_decimal_to_cents_correctly():
    # ARRANGE + ACT: build a Product using our custom constructor,
    # exactly the way create_product() does in the real router.
    product = Product.from_price(
        Decimal("19.99"),
        name="Test Product",
        stock_quantity=10,
    )

    # ASSERT: this is the actual thing we care about proving - that
    # $19.99 becomes EXACTLY 1999 cents, not 1998 or 2000 due to some
    # floating-point rounding artifact sneaking in during the
    # multiplication.
    assert product.price_cents == 1999


def test_price_computed_field_round_trips_correctly():
    # Going the OTHER direction: given cents already stored, does the
    # computed "price" property correctly reconstruct the original
    # Decimal value?
    product = Product(price_cents=1999, name="Test Product", stock_quantity=10)

    assert product.price == Decimal("19.99")


def test_from_price_handles_whole_dollar_amounts():
    # A specific edge case worth testing explicitly - whole-dollar
    # amounts are exactly where naive float multiplication most often
    # goes wrong (e.g. 20.00 * 100 sometimes producing 1999.9999999998
    # in raw floating point, before truncation/rounding "fixes" it by
    # accident, which is a genuinely fragile thing to rely on).
    product = Product.from_price(Decimal("20.00"), name="Test", stock_quantity=1)

    assert product.price_cents == 2000
    assert product.price == Decimal("20.00")