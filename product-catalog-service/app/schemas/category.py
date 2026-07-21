from pydantic import BaseModel, Field


# Same DTO-vs-entity separation principle as User Service: this is
# what a CLIENT sends us when creating a category — never expose the
# Beanie Document (Category) directly as a request/response shape,
# even though Beanie documents already ARE Pydantic models under the
# hood. Keeping this separate still matters: it lets the API's public
# shape evolve independently from the database's internal shape, and
# prevents a client from ever injecting fields we don't intend to
# accept from outside.
class CategoryCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    description: str | None = None


# What we send BACK to a client. Includes the database-generated id,
# which CategoryCreate deliberately does not have (a client can't
# invent their own id).
class CategoryResponse(BaseModel):
    id: str
    name: str
    description: str | None = None

    # Lets this schema be built directly from a Beanie Document object
    # (model_config from_attributes=True), rather than requiring a
    # manual dict conversion every time — Pydantic reads matching
    # attribute names off the source object automatically.
    model_config = {"from_attributes": True}

    @classmethod
    def from_document(cls, category) -> "CategoryResponse":
        return cls(
            id=str(category.id),
            name=category.name,
            description=category.description,
        )