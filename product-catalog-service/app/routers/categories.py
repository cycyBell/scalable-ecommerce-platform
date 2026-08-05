from fastapi import APIRouter, Depends, HTTPException, status

from app.models.category import Category
from app.schemas.category import CategoryCreate, CategoryResponse
from app.core.security import verify_jwt_token


# APIRouter is FastAPI's equivalent of a Java @RestController class —
# a grouping of related endpoints, later "included" into the main app.
router = APIRouter(prefix="/categories", tags=["categories"])


@router.post("", response_model=CategoryResponse, status_code=status.HTTP_201_CREATED, dependencies=[Depends(verify_jwt_token)])
async def create_category(request: CategoryCreate) -> CategoryResponse:

    category = Category(name=request.name, description=request.description)
    await category.insert()
    return CategoryResponse.from_document(category)


@router.get("/{category_id}", response_model=CategoryResponse)
async def get_category(category_id: str) -> CategoryResponse:
    category = await Category.get(category_id)
    if category is None:
        # HTTPException is FastAPI's built-in way to short-circuit a
        # request with a specific status code and message — genuinely
        # similar in spirit to throwing one of our custom exceptions in
        # Java, except here FastAPI handles the response formatting
        # for you automatically, without needing a separate global
        # exception handler class for this common case.
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category not found")
    return CategoryResponse.from_document(category)


@router.get("", response_model=list[CategoryResponse])
async def list_categories() -> list[CategoryResponse]:
    categories = await Category.find_all().to_list()
    return [CategoryResponse.from_document(c) for c in categories]


@router.delete("/{category_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Depends(verify_jwt_token)])
async def delete_category(category_id: str) -> None:

    category = await Category.get(category_id)
    if category is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Category not found")
    await category.delete()