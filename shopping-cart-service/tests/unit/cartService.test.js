/**
 * ==============================================================================
 * UNIT TEST SUITE: Core Cart Business Service (`tests/unit/cartService.test.js`)
 * ==============================================================================
 * 
 * TESTS COVERED:
 * 1. addItem: Validates stock, increments item quantity in Redis, sets 7-day TTL.
 * 2. updateQuantity: Sets exact quantity, or removes item if quantity is 0.
 * 3. removeItem: Deletes field from Redis Hash.
 * 4. clearCart: Purges cart key completely.
 * 5. mergeCarts: Merges guest cart into user cart via Redis pipeline and deletes guest key.
 * ==============================================================================
 */

const { getCart, addItem, updateQuantity, removeItem, clearCart, mergeCarts } = require('../../src/services/cartService');
const catalogService = require('../../src/services/catalogService');
const { getRedisClient, closeRedisConnection } = require('../../src/config/redis');

// Mock ioredis with ioredis-mock for in-memory testing
jest.mock('ioredis', () => {
    const RedisMock = require('ioredis-mock');
    return RedisMock;
});

// Mock catalogService calls
jest.mock('../../src/services/catalogService', () => ({
    validateProductStock: jest.fn().mockResolvedValue({
        id: 'prod-100',
        title: 'Mechanical Keyboard',
        price: 120.00,
        stockQuantity: 15
    }),
    enrichCartItems: jest.fn().mockImplementation(async (hash) => {
        const productIds = Object.keys(hash || {});
        let totalItems = 0;
        let totalAmount = 0.00;

        const items = productIds.map(id => {
            const qty = parseInt(hash[id], 10) || 1;
            totalItems += qty;
            totalAmount += 120.00 * qty;
            return {
                productId: id,
                title: 'Mechanical Keyboard',
                price: 120.00,
                quantity: qty,
                itemTotal: 120.00 * qty,
                isAvailable: true
            };
        });

        return { items, totalItems, totalAmount };
    })
}));

describe('Cart Service Business Logic Unit Tests', () => {

    const testUserCartKey = 'cart:testuser@example.com';
    const testGuestCartKey = 'cart:guest:guest-uuid-999';

    afterEach(async () => {
        const client = getRedisClient();
        await client.flushall();
        jest.clearAllMocks();
    });

    afterAll(async () => {
        await closeRedisConnection();
    });

    test('addItem() should validate stock and add item to Redis hash', async () => {
        const cart = await addItem(testUserCartKey, 'prod-100', 2);

        expect(catalogService.validateProductStock).toHaveBeenCalledWith('prod-100', 2);
        expect(cart.totalItems).toBe(2);
        expect(cart.totalAmount).toBe(240.00);

        const client = getRedisClient();
        const savedQty = await client.hget(testUserCartKey, 'prod-100');
        expect(savedQty).toBe('2');
    });

    test('updateQuantity() should update exact quantity in Redis hash', async () => {
        await addItem(testUserCartKey, 'prod-100', 1);

        const updatedCart = await updateQuantity(testUserCartKey, 'prod-100', 4);

        expect(catalogService.validateProductStock).toHaveBeenCalledWith('prod-100', 4);
        expect(updatedCart.totalItems).toBe(4);

        const client = getRedisClient();
        const savedQty = await client.hget(testUserCartKey, 'prod-100');
        expect(savedQty).toBe('4');
    });

    test('updateQuantity() with 0 should remove item from cart', async () => {
        await addItem(testUserCartKey, 'prod-100', 2);

        const cart = await updateQuantity(testUserCartKey, 'prod-100', 0);

        expect(cart.totalItems).toBe(0);

        const client = getRedisClient();
        const savedQty = await client.hget(testUserCartKey, 'prod-100');
        expect(savedQty).toBeNull();
    });

    test('removeItem() should delete product field from Redis hash', async () => {
        await addItem(testUserCartKey, 'prod-100', 3);

        const cart = await removeItem(testUserCartKey, 'prod-100');

        expect(cart.totalItems).toBe(0);

        const client = getRedisClient();
        const savedQty = await client.hget(testUserCartKey, 'prod-100');
        expect(savedQty).toBeNull();
    });

    test('clearCart() should delete entire cart key from Redis', async () => {
        await addItem(testUserCartKey, 'prod-100', 5);

        const result = await clearCart(testUserCartKey);

        expect(result.success).toBe(true);

        const client = getRedisClient();
        const exists = await client.exists(testUserCartKey);
        expect(exists).toBe(0);
    });

    test('mergeCarts() should merge guest items into user cart and delete guest cart key', async () => {
        const client = getRedisClient();

        // Seed guest cart with 2 items
        await client.hset(testGuestCartKey, 'prod-100', '2');
        // Seed user cart with 1 item
        await client.hset(testUserCartKey, 'prod-100', '1');

        const mergedCart = await mergeCarts(testGuestCartKey, testUserCartKey);

        // Resulting quantity should be 1 + 2 = 3
        expect(mergedCart.totalItems).toBe(3);

        // Guest cart key should be deleted
        const guestExists = await client.exists(testGuestCartKey);
        expect(guestExists).toBe(0);

        // User cart key should contain merged quantity 3
        const userQty = await client.hget(testUserCartKey, 'prod-100');
        expect(userQty).toBe('3');
    });
});
