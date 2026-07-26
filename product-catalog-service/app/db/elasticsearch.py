from elasticsearch import AsyncElasticsearch
from app.models.product import Product
from decimal import Decimal
from app.core.config import settings

# A module-level, shared async client - same pattern as Motor's
# AsyncIOMotorClient: one connection pool, created once, reused
# throughout the app's lifetime, rather than opening a new connection
# per request.
es_client = AsyncElasticsearch(hosts=[settings.elasticsearch_url])

# The index name is the Elasticsearch equivalent of a MongoDB
# collection name, or a SQL table name - where all product documents
# for search purposes will actually live.
PRODUCTS_INDEX = "products"


# Elasticsearch needs an explicit MAPPING before you index real data if
# you want control over exactly how each field is analyzed for search -
# without this, Elasticsearch would auto-guess field types from the
# first document it sees, which can produce subtly wrong search
# behavior (e.g. treating a price as a keyword instead of a proper
# numeric range-queryable field).
async def create_products_index():
    exists = await es_client.indices.exists(index=PRODUCTS_INDEX)
    if exists:
        return

    await es_client.indices.create(
        index=PRODUCTS_INDEX,
        mappings={
            "properties": {
                # "text" type: analyzed, tokenized, and stemmed for
                # free-text relevance search - this is what makes
                # searching "wireless mice" also match a product named
                # "Wireless Mouse" (singular/plural handled via
                # stemming), which a MongoDB regex search would NOT do
                # for you automatically.
                "name": {"type": "text"},
                "description": {"type": "text"},

                # "keyword" type: NOT analyzed - stored and matched as
                # an exact, whole value. Used for fields we want to
                # FILTER on precisely (e.g. filter to exactly this
                # category id), not search fuzzily within.
                "category_ids": {"type": "keyword"},

                # Numeric types enable proper range queries (price
                # between X and Y) - critical for the price-range
                # filter the plan calls for.
                "price": {"type": "scaled_float", "scaling_factor": 100},
                "stock_quantity": {"type": "integer"},
            }
        },
    )




# Converts a MongoDB-backed Product document into the flat, search-
# optimized shape our Elasticsearch mapping expects. This is
# deliberately a SEPARATE shape from ProductResponse (our API-facing
# schema) - Elasticsearch doesn't need every field the API returns
# (there's no need to search by "id" as a text field, for instance),
# and it needs category_ids as plain strings, not Beanie Link objects,
# which Elasticsearch has no concept of at all.
async def index_product(product: Product) -> None:
    document = {
        "name": product.name,
        "description": product.description or "",
        "price": float(product.price),  # scaled_float expects a plain
                                          # number over the wire, not a
                                          # Python Decimal object -
                                          # Elasticsearch's client
                                          # library doesn't know how to
                                          # serialize Decimal directly.
        "stock_quantity": product.stock_quantity,
        "category_ids": [str(link.ref.id) for link in product.categories],
    }

    # Using the MongoDB _id as the Elasticsearch document's id too -
    # this is what makes "index_product" double as both "create" and
    # "update": indexing a document with an id that already exists
    # simply REPLACES the previous version entirely. We never need to
    # ask "does this already exist in Elasticsearch?" first.
    await es_client.index(
        index=PRODUCTS_INDEX,
        id=str(product.id),
        document=document,
    )


# The mirror operation - removes a product from the search index when
# it's deleted from MongoDB. Without this, a deleted product would
# keep showing up in search results forever, since Elasticsearch has
# no idea MongoDB deleted anything unless we explicitly tell it too.
async def remove_product_from_index(product_id: str) -> None:
    try:
        await es_client.delete(index=PRODUCTS_INDEX, id=product_id)
    except Exception:
        # If the document was never indexed in the first place (e.g. it
        # was created before Elasticsearch was wired up, or a previous
        # indexing attempt failed silently), delete() would raise a
        # "not found" error. We deliberately swallow that specific,
        # harmless case here - the END STATE we want (this id is not
        # in the index) is already true, so there's nothing to fix.
        pass





async def search_products(
    query: str | None = None,
    min_price: Decimal | None = None,
    max_price: Decimal | None = None,
    category_id: str | None = None,
    in_stock_only: bool = False,
) -> list[dict]:

    # "must" holds conditions that affect BOTH relevance scoring AND
    # inclusion - this is where the free-text search itself lives.
    must_clauses = []

    # "filter" holds conditions that affect ONLY inclusion, not scoring
    # - this is the key structural distinction in Elasticsearch's bool
    # query. Filters are also more efficiently cacheable by
    # Elasticsearch internally, since they're simple yes/no checks with
    # no relevance math involved.
    filter_clauses = []

    if query:
        must_clauses.append({
            # multi_match searches across MULTIPLE fields at once,
            # combining their relevance scores. "fields" with a boost
            # (name^2) tells Elasticsearch "a match in the name field is
            # twice as important as a match in the description field" -
            # a genuinely useful relevance-tuning tool that has no real
            # equivalent in a simple MongoDB regex search.
            "multi_match": {
                "query": query,
                "fields": ["name^2", "description"],
            }
        })
    else:
        # If no free-text query was given at all (e.g. someone just
        # wants "everything in stock under $50"), match_all ensures we
        # still return results, rather than an empty result set by
        # default.
        must_clauses.append({"match_all": {}})

    if min_price is not None or max_price is not None:
        price_range = {}
        if min_price is not None:
            price_range["gte"] = float(min_price)
        if max_price is not None:
            price_range["lte"] = float(max_price)
        filter_clauses.append({"range": {"price": price_range}})

    if category_id:
        filter_clauses.append({"term": {"category_ids": category_id}})

    if in_stock_only:
        filter_clauses.append({"range": {"stock_quantity": {"gt": 0}}})

    response = await es_client.search(
        index=PRODUCTS_INDEX,
        query={
            "bool": {
                "must": must_clauses,
                "filter": filter_clauses,
            }
        },
    )

    # Elasticsearch wraps actual results inside a fairly deep nested
    # structure (hits.hits[].{_id, _source, _score, ...}) - this
    # extracts just what we actually need: the document id (which
    # Elasticsearch stores separately from the document body itself,
    # hence pulling it from "_id" rather than "_source") plus its
    # fields.
    results = []
    for hit in response["hits"]["hits"]:
        source = hit["_source"]
        results.append({
            "id": hit["_id"],
            "name": source["name"],
            "description": source["description"],
            "price": Decimal(str(source["price"])),
            "stock_quantity": source["stock_quantity"],
        })

    return results