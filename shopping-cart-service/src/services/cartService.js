/**
 * ==============================================================================
 * MODULE: Core Shopping Cart Business Service (`src/services/cartService.js`)
 * ==============================================================================
 * 
 * WHY IS THIS MODULE THE CORE OF THE SERVICE?
 * This module coordinates all Redis data structure interactions for shopping carts.
 * It encapsulates low-level Redis Hash commands (`HINCRBY`, `HSET`, `HDEL`, `HGETALL`, `DEL`, `EXPIRE`)
 * and connects with `catalogService` to guarantee stock availability.
 * 
 * KEY REDIS STRATEGIES DEMONSTRATED:
 * 1. Redis Hashes (`HSET`, `HGETALL`, `HDEL`): Stores cart items as key-value pairs
 *    where sub-field is `productId` and value is stringified quantity.
 * 2. Sliding TTL (`EXPIRE cartKey 604800`): Extends cart lifetime by 7 days on every write.
 * 3. Atomic Multi-Command Pipelines (`client.pipeline()`): Merges guest items into
 *    user cart atomically without race conditions.
 * ==============================================================================
 */

const { getRedisClient } = require('../config/redis');
const catalogService = require('./catalogService');
const { BadRequestError } = require('../middleware/errorHandler');

// Default sliding TTL for shopping carts: 7 days in seconds (7 * 24 * 60 * 60 = 604,800s)
const CART_TTL_SECONDS = 604800;

/**
 * Retrieves full enriched shopping cart details from Redis and Product Catalog Service.
 * 
 * @param {string} cartKey Redis cart key (e.g. 'cart:user@example.com' or 'cart:guest:uuid-123')
 * @returns {Promise<{ cartKey: string, items: Array, totalItems: number, totalAmount: number }>}
 */
async function getCart(cartKey) {
    if (!cartKey) {
        throw new BadRequestError('Cart key is required.');
    }

    const client = getRedisClient();

    // Fetch all product IDs and quantities stored in the Redis Hash
    const rawCartHash = await client.hgetall(cartKey);

    // Enrich raw Redis items with live Product Catalog pricing and stock details
    const enriched = await catalogService.enrichCartItems(rawCartHash);

    return {
        cartKey,
        items: enriched.items,
        totalItems: enriched.totalItems,
        totalAmount: enriched.totalAmount
    };
}

/**
 * Adds an item to the shopping cart, validating stock availability beforehand.
 * 
 * @param {string} cartKey Redis cart key.
 * @param {string} productId ID of product to add.
 * @param {number} quantity Quantity to add (must be > 0).
 * @returns {Promise<Object>} Enriched cart payload.
 */
async function addItem(cartKey, productId, quantity) {
    const qty = parseInt(quantity, 10);
    if (isNaN(qty) || qty <= 0) {
        throw new BadRequestError('Item quantity must be a positive integer greater than 0.');
    }

    const client = getRedisClient();

    // Fetch existing quantity in cart to check total stock limit
    const existingQtyStr = await client.hget(cartKey, productId);
    const existingQty = parseInt(existingQtyStr || '0', 10);
    const targetQuantity = existingQty + qty;

    // Validate that available inventory stock covers the target quantity
    await catalogService.validateProductStock(productId, targetQuantity);

    // Increment item quantity atomically in Redis Hash
    await client.hincrby(cartKey, productId, qty);

    // Refresh sliding 7-day TTL on every cart modification
    await client.expire(cartKey, CART_TTL_SECONDS);

    return await getCart(cartKey);
}

/**
 * Updates or sets an item's exact quantity in the shopping cart.
 * If quantity is 0, removes the item from the cart.
 * 
 * @param {string} cartKey Redis cart key.
 * @param {string} productId ID of product to update.
 * @param {number} quantity Target quantity.
 * @returns {Promise<Object>} Enriched cart payload.
 */
async function updateQuantity(cartKey, productId, quantity) {
    const qty = parseInt(quantity, 10);
    if (isNaN(qty) || qty < 0) {
        throw new BadRequestError('Item quantity must be a non-negative integer (0 or greater).');
    }

    // If quantity is 0, treat as item removal
    if (qty === 0) {
        return await removeItem(cartKey, productId);
    }

    // Validate stock for the new target quantity
    await catalogService.validateProductStock(productId, qty);

    const client = getRedisClient();

    // Set exact quantity in Redis Hash
    await client.hset(cartKey, productId, qty.toString());

    // Refresh sliding 7-day TTL
    await client.expire(cartKey, CART_TTL_SECONDS);

    return await getCart(cartKey);
}

/**
 * Removes a single product item from the shopping cart.
 * 
 * @param {string} cartKey Redis cart key.
 * @param {string} productId ID of product to remove.
 * @returns {Promise<Object>} Enriched cart payload.
 */
async function removeItem(cartKey, productId) {
    if (!productId) {
        throw new BadRequestError('Product ID is required to remove item.');
    }

    const client = getRedisClient();

    // Delete field from Redis Hash
    await client.hdel(cartKey, productId);

    // Refresh sliding TTL if cart still has items
    const remainingFields = await client.hlen(cartKey);
    if (remainingFields > 0) {
        await client.expire(cartKey, CART_TTL_SECONDS);
    }

    return await getCart(cartKey);
}

/**
 * Clears all items from the shopping cart (used after order checkout completion).
 * 
 * @param {string} cartKey Redis cart key.
 * @returns {Promise<{ success: boolean, message: string }>}
 */
async function clearCart(cartKey) {
    if (!cartKey) {
        throw new BadRequestError('Cart key is required to clear cart.');
    }

    const client = getRedisClient();
    await client.del(cartKey);

    return {
        success: true,
        message: `Shopping cart '${cartKey}' cleared successfully.`
    };
}

/**
 * Merges items from an anonymous guest cart into an authenticated user cart.
 * Executed automatically when a user logs in. Uses a Redis atomic pipeline.
 * 
 * @param {string} guestCartKey Guest Redis cart key (e.g. 'cart:guest:uuid-123')
 * @param {string} userCartKey Authenticated User Redis cart key (e.g. 'cart:user@example.com')
 * @returns {Promise<Object>} Enriched user cart payload.
 */
async function mergeCarts(guestCartKey, userCartKey) {
    if (!guestCartKey || !userCartKey) {
        throw new BadRequestError('Both guestCartKey and userCartKey are required to merge carts.');
    }

    const client = getRedisClient();

    // Fetch all items from guest cart
    const guestCartHash = await client.hgetall(guestCartKey);

    if (!guestCartHash || Object.keys(guestCartHash).length === 0) {
        return await getCart(userCartKey);
    }

    // Execute atomic Redis pipeline for multi-command transaction
    const pipeline = client.pipeline();

    for (const [productId, qtyStr] of Object.entries(guestCartHash)) {
        const qty = parseInt(qtyStr, 10) || 1;
        pipeline.hincrby(userCartKey, productId, qty);
    }

    // Refresh user cart 7-day TTL and delete old guest cart
    pipeline.expire(userCartKey, CART_TTL_SECONDS);
    pipeline.del(guestCartKey);

    await pipeline.exec();

    return await getCart(userCartKey);
}

module.exports = {
    getCart,
    addItem,
    updateQuantity,
    removeItem,
    clearCart,
    mergeCarts,
    CART_TTL_SECONDS
};
