from beanie import Document


# A Beanie Document IS a Pydantic model underneath — meaning validation
# and database-mapping are unified here, unlike Java where User (the
# JPA entity) and RegisterRequest (the Pydantic-equivalent DTO) were
# deliberately two separate classes. We'll still build separate
# request/response SCHEMAS in Phase 3 for the same security reasons we
# had in User Service (never expose internal fields, never let a
# client's JSON directly become a database document) — but the
# database-mapping layer itself is simpler here by design.
class Category(Document):
    name: str
    description: str | None = None

    # Settings is Beanie's mechanism for configuring collection-level
    # behavior — the equivalent of @Table(name = "...") in JPA.
    class Settings:
        name = "categories"