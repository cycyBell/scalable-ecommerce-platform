from decimal import Decimal

from beanie import Document, Link, Indexed
from pydantic import Field, field_validator, computed_field

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