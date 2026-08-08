/**
 * ==============================================================================
 * MODULE: Product Catalog Service Microservice Client (`src/services/catalogService.js`)
 * ==============================================================================
 * 
 * WHY IS THIS SERVICE CLIENT NEEDED?
 * Microservices maintain strict domain boundaries. The Shopping Cart Service stores
 * only ephemeral user item selections (`productId` and `quantity`) in Redis. It does
 * NOT store product titles, prices, descriptions, or inventory stock levels.
 * 
 * WHAT DOES THIS MODULE DO?
 * 1. Stock Validation (`validateProductStock`): Calls Product Catalog Service to verify
 *    that a product exists, is active, and has sufficient stock before allowing item additions.
 * 2. Item Enrichment (`enrichCartItems`): Takes raw Redis cart product IDs & quantities,
 *    fetches live pricing and titles from Catalog Service, and computes subtotal amounts.
 * 3. Security Hardening (SSRF & Path Traversal Prevention):
 *    - Strict regex format validation (/^[a-zA-Z0-9_-]{1,128}$/).
 *    - URL component encoding via `encodeURIComponent` to prevent path traversal attacks (e.g. `../`).
 *    - Fixed `baseURL` prevent host-spoofing SSRF.
 * 4. Resiliency & Graceful Degradation: Handles missing/deleted products without crashing,
 *    and configures HTTP timeouts (5000ms).
 * ==============================================================================
 */

const axios = require('axios');
const config = require('../config/env');
const { NotFoundError, BadRequestError } = require('../middleware/errorHandler');

// Safe Product ID Regex: Allows only alphanumeric characters, dashes, and underscores (1-128 chars).
// Defends against SSRF, Directory Traversal ('../'), and URL parameter injection.
const SAFE_PRODUCT_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;

/**
 * Validates that a productId string conforms to safe format standards without path traversal chars.
 * @param {string} productId Product identifier to inspect.
 * @returns {string} Sanitized, URL-encoded product ID string.
 */
function sanitizeAndValidateProductId(productId) {
    if (!productId || typeof productId !== 'string') {
        throw new BadRequestError('Product ID is required and must be a non-empty string.');
    }

    const trimmed = productId.trim();

    // Prevent path traversal characters explicitly
    if (trimmed.includes('..') || trimmed.includes('/') || trimmed.includes('\\')) {
        throw new BadRequestError(`Invalid product ID '${productId}'. Path traversal characters are strictly forbidden.`);
    }

    // Enforce strict character whitelist
    if (!SAFE_PRODUCT_ID_REGEX.test(trimmed)) {
        throw new BadRequestError(`Invalid product ID format '${productId}'. IDs must contain only alphanumeric characters, dashes, or underscores.`);
    }

    return encodeURIComponent(trimmed);
}

// Create a pre-configured Axios instance for Product Catalog Service HTTP calls with fixed baseURL
const catalogClient = axios.create({
    baseURL: config.services.catalogUrl,
    timeout: 5000, // 5 second timeout to prevent hanging connections
    headers: {
        'Accept': 'application/json',
        'User-Agent': 'shopping-cart-service/1.0'
    }
});

/**
 * Validates product existence, active status, and stock availability with Product Catalog Service.
 * 
 * @param {string} productId Unique identifier of the product to validate.
 * @param {number} requestedQuantity Quantity the customer intends to add or set in cart.
 * @returns {Promise<{ id: string, title: string, price: number, stockQuantity: number, imageUrl: string|null }>}
 * @throws {NotFoundError} If product is not found in Catalog Service.
 * @throws {BadRequestError} If quantity is invalid, product is inactive, or stock is insufficient.
 */
async function validateProductStock(productId, requestedQuantity) {
    const cleanId = sanitizeAndValidateProductId(productId);

    const qty = parseInt(requestedQuantity, 10);
    if (isNaN(qty) || qty <= 0) {
        throw new BadRequestError(`Requested item quantity must be a positive integer greater than 0. Received: ${requestedQuantity}`);
    }

    try {
        // Issue HTTP GET request to Product Catalog Service endpoint with sanitized, encoded path
        const response = await catalogClient.get(`/products/${cleanId}`);
        const product = response.data;

        if (!product) {
            throw new NotFoundError(`Product with ID '${productId}' was not found in catalog.`);
        }

        // Check if product is active for purchase
        if (product.is_active === false) {
            throw new BadRequestError(`Product '${product.title || productId}' is currently inactive and unavailable for purchase.`);
        }

        // Check inventory stock availability
        const availableStock = parseInt(product.stock_quantity ?? 0, 10);
        if (availableStock < qty) {
            throw new BadRequestError(
                `Insufficient stock for product '${product.title || productId}'. ` +
                `Requested: ${qty}, Available in stock: ${availableStock}.`
            );
        }

        return {
            id: product.id || productId,
            title: product.title || 'Unknown Product',
            price: parseFloat(product.price || 0),
            stockQuantity: availableStock,
            imageUrl: product.image_url || null
        };
    } catch (err) {
        // Handle Axios HTTP Response Errors (e.g. 404 Not Found from Catalog Service)
        if (err.response) {
            if (err.response.status === 404) {
                throw new NotFoundError(`Product with ID '${productId}' does not exist in catalog.`);
            }
            const catalogMsg = err.response.data?.detail || err.response.data?.message || err.message;
            throw new BadRequestError(`Product Catalog Service validation failed: ${catalogMsg}`);
        }

        // Handle network timeout or connection errors
        if (err.code === 'ECONNREFUSED' || err.code === 'ETIMEDOUT') {
            console.error(`[Catalog Client Error] Unable to connect to Product Catalog Service at ${config.services.catalogUrl}: ${err.message}`);
            throw new BadRequestError(`Product Catalog Service is temporarily unreachable. Please try again shortly.`);
        }

        // Re-throw custom ApiErrors
        throw err;
    }
}

/**
 * Enriches a raw Redis cart hash payload with live product details from Catalog Service.
 * Calculates subtotal prices, total cart items, and total amount.
 * 
 * @param {Object} cartHash Key-value map of { productId: quantity } or JSON string items from Redis.
 * @returns {Promise<{ items: Array, totalItems: number, totalAmount: number }>}
 */
async function enrichCartItems(cartHash) {
    if (!cartHash || typeof cartHash !== 'object' || Object.keys(cartHash).length === 0) {
        return {
            items: [],
            totalItems: 0,
            totalAmount: 0.00
        };
    }

    const productIds = Object.keys(cartHash);

    // Issue parallel HTTP lookup calls using Promise.all for fault tolerance
    const lookupPromises = productIds.map(async (productId) => {
        let cleanId;
        try {
            cleanId = sanitizeAndValidateProductId(productId);
        } catch (validationErr) {
            // If an invalid product ID somehow landed in Redis, gracefully mark it as unavailable
            return {
                productId,
                title: 'Invalid Product Identifier',
                price: 0.00,
                quantity: 1,
                itemTotal: 0.00,
                imageUrl: null,
                isAvailable: false,
                availableStock: 0,
                availabilityError: validationErr.message
            };
        }

        const rawValue = cartHash[productId];
        let quantity = 1;

        // Parse quantity stored in Redis Hash field
        if (typeof rawValue === 'string' && rawValue.startsWith('{')) {
            try {
                const parsed = JSON.parse(rawValue);
                quantity = parseInt(parsed.quantity || 1, 10);
            } catch (e) {
                quantity = parseInt(rawValue, 10) || 1;
            }
        } else {
            quantity = parseInt(rawValue, 10) || 1;
        }

        try {
            const response = await catalogClient.get(`/products/${cleanId}`);
            const product = response.data;

            const unitPrice = parseFloat(product.price || 0);
            const itemTotal = parseFloat((unitPrice * quantity).toFixed(2));
            const availableStock = parseInt(product.stock_quantity ?? 0, 10);
            const isAvailable = product.is_active !== false && availableStock >= quantity;

            return {
                productId,
                title: product.title || 'Unknown Product',
                price: unitPrice,
                quantity,
                itemTotal,
                imageUrl: product.image_url || null,
                isAvailable,
                availableStock,
                availabilityError: !isAvailable ? (availableStock < quantity ? 'Insufficient stock' : 'Product inactive') : null
            };
        } catch (err) {
            // Handle missing/deleted catalog products gracefully without breaking the cart
            return {
                productId,
                title: 'Product No Longer Available',
                price: 0.00,
                quantity,
                itemTotal: 0.00,
                imageUrl: null,
                isAvailable: false,
                availableStock: 0,
                availabilityError: 'Product removed from catalog'
            };
        }
    });

    const results = await Promise.all(lookupPromises);

    // Compute cart totals
    let totalItems = 0;
    let totalAmount = 0.00;

    results.forEach(item => {
        totalItems += item.quantity;
        totalAmount += item.itemTotal;
    });

    return {
        items: results,
        totalItems,
        totalAmount: parseFloat(totalAmount.toFixed(2))
    };
}

module.exports = {
    catalogClient,
    sanitizeAndValidateProductId,
    validateProductStock,
    enrichCartItems
};
