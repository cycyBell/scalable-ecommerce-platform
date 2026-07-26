from beanie import Link
from fastapi import APIRouter, HTTPException, status

from app.models.category import Category
from app.models.product import Product
from app.schemas.product import ProductCreate, ProductResponse, ProductUpdate,StockAdjustment
from app.db.elasticsearch import index_product, remove_product_from_index
from decimal import Decimal
from app.db.elasticsearch import search_products
from app.schemas.product import ProductSearchResult


router = APIRouter(prefix="/products", tags=["products"])


@router.post("", response_model=ProductResponse, status_code=status.HTTP_201_CREATED)
async def create_product(request: ProductCreate) -> ProductResponse:
    # Resolve category_ids (plain strings from the client) into actual
    # Link[Category] references Beanie needs internally. We fetch each
    # referenced Category to confirm it genuinely exists BEFORE saving
    # the product — silently accepting a bogus category id would leave
    # the product referencing something that doesn't exist.
    category_links: list[Link[Category]] = []
    for cat_id in request.category_ids:
        category = await Category.get(cat_id)
        if category is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Category {cat_id} does not exist",
            )
        category_links.append(category)

    product = Product.from_price(
        request.price,
        name=request.name,
        description=request.description,
        stock_quantity=request.stock_quantity,
        categories=category_links,
    )
    await product.insert()

    # NEW: index into Elasticsearch immediately after the MongoDB write
    # succeeds. Notice the ORDER here matters: we only index into
    # search AFTER confirming the MongoDB write actually succeeded -
    # if insert() had thrown, we'd never reach this line, and we'd
    # correctly avoid indexing a product that doesn't actually exist.
    await index_product(product)
    return ProductResponse.from_document(product)


@router.get("/search", response_model=list[ProductSearchResult])
async def search_products_endpoint(
    q: str | None = None,
    min_price: Decimal | None = None,
    max_price: Decimal | None = None,
    category_id: str | None = None,
    in_stock: bool = False,
) -> list[ProductSearchResult]:
    # FastAPI automatically parses ALL of these from query string
    # parameters (e.g. ?q=mouse&min_price=10&in_stock=true) purely
    # based on the function's type hints - the same automatic-binding
    # convenience we relied on throughout this whole service, just
    # applied to query params instead of a request body this time.
    results = await search_products(
        query=q,
        min_price=min_price,
        max_price=max_price,
        category_id=category_id,
        in_stock_only=in_stock,
    )
    return [ProductSearchResult(**r) for r in results]

@router.get("/{product_id}", response_model=ProductResponse)
async def get_product(product_id: str) -> ProductResponse:
    product = await Product.get(product_id)
    if product is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
    return ProductResponse.from_document(product)


@router.get("", response_model=list[ProductResponse])
async def list_products(skip: int = 0, limit: int = 20) -> list[ProductResponse]:
    # skip/limit implement basic pagination — FastAPI automatically
    # parses these from query params (e.g. ?skip=20&limit=20), validates
    # they're integers, with no extra annotation needed beyond the
    # type hint itself.
    products = await Product.find_all().skip(skip).limit(limit).to_list()
    return [ProductResponse.from_document(p) for p in products]


@router.put("/{product_id}", response_model=ProductResponse)
async def update_product(product_id: str, request: ProductUpdate) -> ProductResponse:
    product = await Product.get(product_id)
    if product is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")

    # Only update fields the client actually provided — exclude_unset=True
    # means "give me only the fields explicitly set in the request,"
    # so leaving a field out of the PUT body doesn't accidentally
    # overwrite it with None.
    update_data = request.model_dump(exclude_unset=True)

    if "price" in update_data:
        product.price_cents = int(update_data.pop("price") * 100)

    for field, value in update_data.items():
        setattr(product, field, value)

    await product.save()

    # NEW: index into Elasticsearch immediately after the MongoDB write
    # succeeds. Notice the ORDER here matters: we only index into
    # search AFTER confirming the MongoDB write actually succeeded -
    # if insert() had thrown, we'd never reach this line, and we'd
    # correctly avoid indexing a product that doesn't actually exist.
    await index_product(product)
    return ProductResponse.from_document(product)


@router.delete("/{product_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_product(product_id: str) -> None:
    product = await Product.get(product_id)
    if product is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
    await product.delete()
    # NEW: keep Elasticsearch honest about deletions too.
    await remove_product_from_index(product_id)




@router.patch("/{product_id}/stock", response_model=ProductResponse)
async def adjust_product_stock(product_id: str, request: StockAdjustment) -> ProductResponse:
    # Fetch the CURRENT state first, purely to distinguish "product
    # doesn't exist at all" from "product exists but stock is
    # insufficient" - both would otherwise look identical from
    # adjust_stock()'s return value alone.
    existing = await Product.get(product_id)
    if existing is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")

    updated = await Product.adjust_stock(product_id, request.quantity_change)

    # If adjust_stock's atomic filter didn't match (insufficient stock
    # for a removal), the resulting document's stock_quantity will be
    # UNCHANGED from what we fetched above - that's our signal the
    # operation was rejected, not silently ignored without us noticing.
    if request.quantity_change < 0 and updated.stock_quantity == existing.stock_quantity:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Insufficient stock for this operation",
        )
    # NEW
    await index_product(updated)
    return ProductResponse.from_document(updated)