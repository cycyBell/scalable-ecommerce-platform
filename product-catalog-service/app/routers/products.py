from beanie import Link
from fastapi import APIRouter, HTTPException, status

from app.models.category import Category
from app.models.product import Product
from app.schemas.product import ProductCreate, ProductResponse, ProductUpdate

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
    return ProductResponse.from_document(product)


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
    return ProductResponse.from_document(product)


@router.delete("/{product_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_product(product_id: str) -> None:
    product = await Product.get(product_id)
    if product is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Product not found")
    await product.delete()