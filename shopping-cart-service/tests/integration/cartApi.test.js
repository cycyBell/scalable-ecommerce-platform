/**
 * ==============================================================================
 * INTEGRATION TEST SUITE: Shopping Cart REST API Endpoints (`tests/integration/cartApi.test.js`)
 * ==============================================================================
 * 
 * TESTS COVERED:
 * 1. POST /cart/items (Guest): Adds item using X-Guest-Id header.
 * 2. GET /cart (Guest): Fetches enriched guest cart.
 * 3. POST /cart/items (User): Adds item using Bearer JWT token.
 * 4. PUT /cart/items/:productId: Updates item quantity.
 * 5. POST /cart/merge: Merges guest cart into user cart upon login.
 * 6. DELETE /cart/items/:productId: Removes product item.
 * 7. DELETE /cart: Clears full cart.
 * 8. Error Handling: Rejects unauthenticated merge requests with HTTP 401.
 * ==============================================================================
 */

const request = require('supertest');
const jwt = require('jsonwebtoken');
const app = require('../../src/index');
const config = require('../../src/config/env');
const { getRedisClient, closeRedisConnection } = require('../../src/config/redis');
const catalogService = require('../../src/services/catalogService');

// Mock ioredis with ioredis-mock for in-memory integration testing
jest.mock('ioredis', () => {
    const RedisMock = require('ioredis-mock');
    return RedisMock;
});

// Mock catalogService product lookups
jest.mock('../../src/services/catalogService', () => ({
    validateProductStock: jest.fn().mockResolvedValue({
        id: 'prod-456',
        title: 'Ergonomic Chair',
        price: 199.99,
        stockQuantity: 20
    }),
    enrichCartItems: jest.fn().mockImplementation(async (hash) => {
        const productIds = Object.keys(hash || {});
        let totalItems = 0;
        let totalAmount = 0.00;

        const items = productIds.map(id => {
            const qty = parseInt(hash[id], 10) || 1;
            totalItems += qty;
            totalAmount += 199.99 * qty;
            return {
                productId: id,
                title: 'Ergonomic Chair',
                price: 199.99,
                quantity: qty,
                itemTotal: 199.99 * qty,
                isAvailable: true
            };
        });

        return { items, totalItems, totalAmount };
    })
}));

describe('Shopping Cart REST API Integration Tests', () => {

    let userAuthToken;
    const testUserEmail = 'customer@example.com';
    const testGuestId = 'guest-uuid-888';

    beforeAll(() => {
        // Generate valid Bearer JWT access token matching User Service signing secret
        userAuthToken = jwt.sign(
            { sub: testUserEmail, roles: ['CUSTOMER'] },
            config.jwt.secret,
            { expiresIn: '1h' }
        );
    });

    afterEach(async () => {
        const client = getRedisClient();
        await client.flushall();
        jest.clearAllMocks();
    });

    afterAll(async () => {
        await closeRedisConnection();
    });

    test('POST /cart/items should add item for anonymous guest using X-Guest-Id header', async () => {
        const response = await request(app)
            .post('/cart/items')
            .set('X-Guest-Id', testGuestId)
            .send({
                productId: 'prod-456',
                quantity: 2
            });

        expect(response.status).toBe(201);
        expect(response.body).toHaveProperty('cartKey', `cart:guest:${testGuestId}`);
        expect(response.body.totalItems).toBe(2);
        expect(response.body.totalAmount).toBe(399.98);
    });

    test('GET /cart should retrieve enriched guest cart', async () => {
        // Add item first
        await request(app)
            .post('/cart/items')
            .set('X-Guest-Id', testGuestId)
            .send({ productId: 'prod-456', quantity: 1 });

        const response = await request(app)
            .get('/cart')
            .set('X-Guest-Id', testGuestId);

        expect(response.status).toBe(200);
        expect(response.body.items.length).toBe(1);
        expect(response.body.items[0]).toHaveProperty('title', 'Ergonomic Chair');
    });

    test('POST /cart/items should add item for authenticated user using Bearer JWT', async () => {
        const response = await request(app)
            .post('/cart/items')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({
                productId: 'prod-456',
                quantity: 1
            });

        expect(response.status).toBe(201);
        expect(response.body).toHaveProperty('cartKey', `cart:${testUserEmail}`);
        expect(response.body.totalItems).toBe(1);
    });

    test('PUT /cart/items/:productId should update exact quantity', async () => {
        await request(app)
            .post('/cart/items')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({ productId: 'prod-456', quantity: 1 });

        const response = await request(app)
            .put('/cart/items/prod-456')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({ quantity: 5 });

        expect(response.status).toBe(200);
        expect(response.body.totalItems).toBe(5);
    });

    test('POST /cart/merge should merge guest cart into authenticated user cart', async () => {
        // 1. Add item to guest cart
        await request(app)
            .post('/cart/items')
            .set('X-Guest-Id', testGuestId)
            .send({ productId: 'prod-456', quantity: 2 });

        // 2. Add item to user cart
        await request(app)
            .post('/cart/items')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({ productId: 'prod-456', quantity: 1 });

        // 3. Issue merge request
        const response = await request(app)
            .post('/cart/merge')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({ guestId: testGuestId });

        expect(response.status).toBe(200);
        expect(response.body.totalItems).toBe(3); // 1 + 2 = 3
    });

    test('DELETE /cart/items/:productId should remove single item from cart', async () => {
        await request(app)
            .post('/cart/items')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({ productId: 'prod-456', quantity: 2 });

        const response = await request(app)
            .delete('/cart/items/prod-456')
            .set('Authorization', `Bearer ${userAuthToken}`);

        expect(response.status).toBe(200);
        expect(response.body.totalItems).toBe(0);
    });

    test('DELETE /cart should clear full cart key', async () => {
        await request(app)
            .post('/cart/items')
            .set('Authorization', `Bearer ${userAuthToken}`)
            .send({ productId: 'prod-456', quantity: 3 });

        const response = await request(app)
            .delete('/cart')
            .set('Authorization', `Bearer ${userAuthToken}`);

        expect(response.status).toBe(200);
        expect(response.body).toHaveProperty('success', true);
    });

    test('POST /cart/merge without Bearer JWT should return 401 Unauthorized', async () => {
        const response = await request(app)
            .post('/cart/merge')
            .send({ guestId: testGuestId });

        expect(response.status).toBe(401);
        expect(response.body.error).toHaveProperty('code', 'UNAUTHORIZED');
    });
});
