from decimal import Decimal

from beanie import Document, Link, Indexed
from pydantic import Field, field_validator, computed_field

from beanie import PydanticObjectId
from beanie.operators import GTE, Inc
from app.models.category import Category


class Product(Document):
    name: Indexed(str, unique=True)
    description: str | None = None

    # Stored in MongoDB as a plain integer (cents) — simple, exact,
    # no BSON/Decimal128 conversion headaches. E.g. $19.99 -> 1999.
    price_cents: int = Field(gt=0)
    
    stock_quantity: int = Field(ge=0)
    categories: list[Link[Category]] = []

    class Settings:
        name = "products"

    # A computed_field exposes a Decimal-based "price" property
    # derived from price_cents, WITHOUT storing it separately in
    # MongoDB — this is what the rest of our application code (and,
    # later, our API responses) will actually interact with, so nobody
    # writing business logic has to think in cents directly.
    @computed_field
    @property
    def price(self) -> Decimal:
        return Decimal(self.price_cents) / 100

    # A convenient constructor-style helper: lets calling code create
    # a Product by passing a normal Decimal price (e.g. Decimal("19.99")),
    # and handles the cents conversion internally, so nobody outside
    # this class needs to remember to do the multiplication themselves.
    @classmethod
    def from_price(cls, price: Decimal, **kwargs) -> "Product":
        price_cents = int(price * 100)
        return cls(price_cents=price_cents, **kwargs)

    @classmethod
    async def adjust_stock(cls, product_id: str, quantity_change: int) -> "Product | None":
        

        # Build the base filter: match this exact product by id.
        filters = [cls.id == PydanticObjectId(product_id)]

        # If we're REMOVING stock (a negative quantity_change), add a
        # second condition to the SAME atomic query: the product's
        # CURRENT stock_quantity must already be at least as large as
        # the amount we're trying to remove. If this condition isn't
        # met, MongoDB simply finds no matching document, and the whole
        # operation does nothing - there is no window where a second,
        # concurrent request could "sneak in" between a check and a
        # write, because the check IS the write's own matching
        # condition, evaluated atomically by MongoDB itself.
        if quantity_change < 0:
            filters.append(GTE(cls.stock_quantity, abs(quantity_change)))

        # find_one_and_update performs the atomic operation: locate a
        # document matching ALL filters above, and if (and only if) one
        # is found, apply Inc(...) - MongoDB's native atomic
        # increment/decrement - to stock_quantity, in the same
        # indivisible step.
        updated_product = await cls.find_one(*filters).update(
            Inc({cls.stock_quantity: quantity_change})
        )

        # If no document matched (either the product doesn't exist, OR
        # - critically - stock was insufficient for a removal), Beanie
        # returns a result indicating zero documents modified. We
        # re-fetch to return the current state either way, letting the
        # caller (our router) decide how to interpret "nothing changed."
        return await cls.get(product_id)