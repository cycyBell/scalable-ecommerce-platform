/**
 * ==============================================================================
 * UNIT TEST SUITE: Redis Configuration & Connection Lifecycle
 * ==============================================================================
 * 
 * WHY IS THIS TEST IMPORTANT?
 * Tests ensure that our Redis connection manager initializes correctly, handles
 * client singletons properly, and returns accurate healthcheck status reports.
 * 
 * USES `ioredis-mock`:
 * Emulates Redis commands in-memory without requiring a live running Redis server
 * during automated CI/CD unit testing runs.
 * ==============================================================================
 */

const { getRedisClient, checkRedisHealth, closeRedisConnection } = require('../../src/config/redis');

// Mock ioredis with ioredis-mock for isolated unit testing
jest.mock('ioredis', () => {
    const RedisMock = require('ioredis-mock');
    return RedisMock;
});

describe('Redis Configuration & Connection Manager Unit Tests', () => {
    let consoleSpy;

    beforeAll(() => {
        // Mute console output during test runs to keep test reports clean
        consoleSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        jest.spyOn(console, 'warn').mockImplementation(() => {});
        jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterAll(() => {
        consoleSpy.mockRestore();
    });

    afterEach(async () => {
        await closeRedisConnection();
    });

    test('getRedisClient() should return a singleton Redis client instance', () => {
        const client1 = getRedisClient();
        const client2 = getRedisClient();

        expect(client1).toBeDefined();
        expect(client2).toBeDefined();
        // Verify both references point to the exact same instance (Singleton Pattern)
        expect(client1).toBe(client2);
    });

    test('checkRedisHealth() should return UP status when Redis responds to PING', async () => {
        const health = await checkRedisHealth();

        expect(health).toHaveProperty('status', 'UP');
        expect(health).toHaveProperty('latencyMs');
        expect(typeof health.latencyMs).toBe('number');
        expect(health).toHaveProperty('host');
    });

    test('closeRedisConnection() should cleanly close active client instance', async () => {
        const client = getRedisClient();
        expect(client).toBeDefined();

        await closeRedisConnection();

        // Getting client again after close should recreate a new client instance
        const newClient = getRedisClient();
        expect(newClient).not.toBe(client);
    });
});
