# Implementation Plan: Task 2 - Scalability & Query Safety (Pagination & Reconciliation Batching)

This plan details the implementation of **Scalability & Query Safety** improvements for `product-catalog-service` to eliminate memory exhaustion vectors in background workers and safeguard API endpoints.

---

## User Review Required

> [!IMPORTANT]
> **Key Enhancements:**
> 1. **Batching Background Reconciliation:** `reconcile_products_index()` will no longer load the entire MongoDB product catalog into memory at once (`Product.find_all().to_list()`). It will stream documents in batches (e.g., 500 at a time) and query Elasticsearch using paginated batch scans.
> 2. **Enforcing API Pagination Bounds:** `GET /products` will enforce strict upper limits (`Query(default=20, ge=1, le=100)`) and non-negative offsets (`Query(default=0, ge=0)`). Requests attempting to fetch excessive limits (e.g., `?limit=100000`) will be rejected immediately with HTTP 422.

---

## Proposed Changes

### 1. `product-catalog-service/app/services`

#### [MODIFY] [reconciliation.py](file:///c:/Users/ADMIN/Documents/Projects/microservices/scalable-ecommerce-platform-v2/product-catalog-service/app/services/reconciliation.py)
* Refactor `reconcile_products_index()` to:
  * Stream MongoDB IDs using cursor iteration in chunks of `BATCH_SIZE = 500`.
  * Paginate Elasticsearch index scans using `search_after` or chunked search queries.
  * Compare missing and orphaned IDs per batch chunk to prevent memory spikes on large catalogs.

---

### 2. `product-catalog-service/app/routers`

#### [MODIFY] [products.py](file:///c:/Users/ADMIN/Documents/Projects/microservices/scalable-ecommerce-platform-v2/product-catalog-service/app/routers/products.py)
* Update `list_products` signature:
  ```python
  @router.get("", response_model=list[ProductResponse])
  async def list_products(
      skip: int = Query(default=0, ge=0, description="Offset for pagination"),
      limit: int = Query(default=20, ge=1, le=100, description="Maximum items per page (max 100)"),
  ) -> list[ProductResponse]:
  ```

---

### 3. Unit & Integration Testing

#### [NEW] [test_reconciliation.py](file:///c:/Users/ADMIN/Documents/Projects/microservices/scalable-ecommerce-platform-v2/product-catalog-service/tests/unit/test_reconciliation.py)
* Add unit tests verifying batched reconciliation and drift resolution.

---

## Verification Plan

### Automated Tests
* Run `pytest` unit tests:
  ```powershell
  venv\Scripts\pytest.exe tests/unit
  ```

### Manual Verification
1. Test `GET /products?limit=200` to verify HTTP 422 Unprocessable Entity validation response.
2. Test `GET /products?skip=0&limit=50` to verify valid paginated response.
3. Trigger reconciliation run and inspect logs to confirm batch execution.

# Walkthrough - Task 2: Scalability & Query Safety (Pagination & Reconciliation Batching)

We have successfully implemented **Task 2: Scalability & Query Safety** in `product-catalog-service` inside `scalable-ecommerce-platform-v2`.

---

## Completed Task 2 Changes

### 1. Batched Background Reconciliation Service
#### [MODIFY] [reconciliation.py](file:///c:/Users/ADMIN/Documents/Projects/microservices/scalable-ecommerce-platform-v2/product-catalog-service/app/services/reconciliation.py)
* Refactored `reconcile_products_index()` to iterate over MongoDB products in chunked batches (`BATCH_SIZE = 500`) using cursor offset streaming instead of loading all products into RAM (`Product.find_all().to_list()`).
* Scanned Elasticsearch documents in batches using `search_after` sorting by `_id`.
* Compared batch chunks against Elasticsearch document IDs and cleaned up missing or orphaned index entries per batch.

### 2. Enforced API Pagination Guardrails
#### [MODIFY] [products.py](file:///c:/Users/ADMIN/Documents/Projects/microservices/scalable-ecommerce-platform-v2/product-catalog-service/app/routers/products.py)
* Configured `Query` parameter bounds on `GET /products`:
  ```python
  skip: int = Query(default=0, ge=0, description="Offset for pagination"),
  limit: int = Query(default=20, ge=1, le=100, description="Maximum items per page (max 100)")
  ```

### 3. Unit Tests
#### [NEW] [test_reconciliation.py](file:///c:/Users/ADMIN/Documents/Projects/microservices/scalable-ecommerce-platform-v2/product-catalog-service/tests/unit/test_reconciliation.py)
* Created unit tests verifying batched missing-product reindexing and orphaned-product removal.

---

## Verification Results

### 1. Unit Tests
Ran `venv\Scripts\pytest.exe tests/unit`:
```text
tests\unit\test_change_stream_listener.py ..                             [ 28%]
tests\unit\test_product_model.py ...                                     [ 71%]
tests\unit\test_reconciliation.py ..                                     [100%]

============================== 7 passed in 0.19s ==============================
```

### 2. API Pagination Validation
* **`GET /products?limit=200`** -> Returned `422 Unprocessable Entity`:
  ```json
  {
    "detail": [
      {
        "type": "less_than_equal",
        "loc": ["query", "limit"],
        "msg": "Input should be less than or equal to 100",
        "input": "200",
        "ctx": { "le": 100 }
      }
    ]
  }
  ```
* **`GET /products?limit=50`** -> Returned `200 OK` with valid paginated results.
