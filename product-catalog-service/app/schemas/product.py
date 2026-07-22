from decimal import Decimal

from pydantic import BaseModel, Field


class ProductCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    description: str | None = None
    price: Decimal = Field(gt=0, decimal_places=2)
    stock_quantity: int = Field(ge=0)
    category_ids: list[str] = []


class ProductUpdate(BaseModel):
    # Every field optional here, since a PUT/PATCH request might only
    # want to change one or two fields, not resend the entire product.
    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    price: Decimal | None = Field(default=None, gt=0, decimal_places=2)
    stock_quantity: int | None = Field(default=None, ge=0)


class ProductResponse(BaseModel):
    id: str
    name: str
    description: str | None
    price: Decimal
    stock_quantity: int
    category_ids: list[str]

    @classmethod
    def from_document(cls, product) -> "ProductResponse":
        return cls(
            id=str(product.id),
            name=product.name,
            description=product.description,
            price=product.price,  # the computed_field we built earlier
            stock_quantity=product.stock_quantity,
            category_ids=[str(c.ref.id if hasattr(c, "ref") else c.id) for c in product.categories],
        )
    



# A dedicated, narrow request shape — deliberately NOT reusing
# ProductUpdate here. Stock adjustment is a fundamentally different
# OPERATION from a general product edit: it's a relative change
# ("decrease by 3"), not an absolute overwrite ("set stock_quantity to
# X"). Keeping it as its own schema makes that distinction explicit and
# prevents this sensitive operation from being accidentally reachable
# through the general-purpose update endpoint.
class StockAdjustment(BaseModel):
    # A positive number means "add stock" (e.g. restocking), a negative
    # number means "remove stock" (e.g. a sale). This single field
    # handles both directions cleanly via MongoDB's $inc operator,
    # which we'll use below.
    quantity_change: int = Field(..., description="Positive to add stock, negative to remove")