/**
 * ==============================================================================
 * MODULE: Redis Client & Connection Lifecycle Manager (`src/config/redis.js`)
 * ==============================================================================
 * 
 * WHY REDIS FOR SHOPPING CARTS?
 * 1. Ultra-Low Latency: Redis stores data in-memory, serving read and write requests
 *    in sub-millisecond speeds (< 1ms), critical for high-churn cart operations.
 * 2. Automatic Ephemeral TTL: Carts are temporary. Redis native TTL (Time-To-Live)
 *    automatically cleans up abandoned shopping carts without database overhead.
 * 3. Atomic Hash Operations: Redis commands like `HINCRBY` (increment quantity) run
 *    atomically, preventing race conditions during rapid cart item quantity updates.
 * 
 * WHAT DOES THIS MODULE DO?
 * - Initializes a robust `ioredis` singleton client connection.
 * - Authenticates securely using `REDIS_PASSWORD`.
 * - Implements exponential backoff retry logic for automatic network recovery.
 * - Provides healthcheck helper `checkRedisHealth()` for service monitoring.
 * - Provides graceful shutdown helper `closeRedisConnection()`.
 * ==============================================================================
 */

const Redis = require('ioredis');
const config = require('./env');

// Singleton instance variable holding the active Redis connection
let redisClient = null;

/**
 * Helper to conditionally log messages only when not in automated unit test mode
 */
function logInfo(msg) {
    if (config.nodeEnv !== 'test') {
        console.log(msg);
    }
}

function logWarn(msg) {
    if (config.nodeEnv !== 'test') {
        console.warn(msg);
    }
}

function logError(msg) {
    if (config.nodeEnv !== 'test') {
        console.error(msg);
    }
}

/**
 * Creates or returns the singleton Redis client instance.
 * Using a singleton pattern ensures our Express microservice reuses a single,
 * high-performance TCP connection pool rather than opening redundant connections.
 * 
 * @returns {Redis} Active ioredis client instance.
 */
function getRedisClient() {
    if (redisClient) {
        return redisClient;
    }

    logInfo(`[Redis Config] Connecting to Redis server at ${config.redis.host}:${config.redis.port}...`);

    redisClient = new Redis({
        host: config.redis.host,
        port: config.redis.port,
        password: config.redis.password,
        
        // Maximum retries per command before throwing an error to caller
        maxRetriesPerRequest: 3,

        // Enable offline queue: Buffers commands if Redis briefly drops TCP connection
        enableOfflineQueue: true,

        /**
         * Exponential Backoff Retry Strategy for Reconnection.
         * If Redis restarts or network drops, ioredis will attempt reconnection
         * with increasing time intervals (up to 3000ms max delay).
         * 
         * @param {number} times Number of failed connection attempts.
         * @returns {number} Delay in milliseconds before next retry.
         */
        retryStrategy(times) {
            const delay = Math.min(times * 100, 3000);
            logWarn(`[Redis Connection Warning] Connection attempt #${times} failed. Retrying in ${delay}ms...`);
            return delay;
        }
    });

    // ==============================================================================
    // EVENT LISTENERS FOR CONNECTION LIFECYCLE MONITORING
    // ==============================================================================

    // Emitted when TCP connection to Redis server host is successfully opened
    redisClient.on('connect', () => {
        logInfo(`[Redis Lifecycle] TCP connection opened to ${config.redis.host}:${config.redis.port}`);
    });

    // Emitted when Redis server completes authentication handshake and is ready for commands
    redisClient.on('ready', () => {
        logInfo(`[Redis Lifecycle] ✅ Redis client authenticated and ready to execute commands.`);
    });

    // Emitted whenever a Redis network or protocol error occurs
    redisClient.on('error', (err) => {
        logError(`[Redis Error] Connection failure: ${err.message}`);
    });

    // Emitted when TCP connection drops and reconnection attempt begins
    redisClient.on('reconnecting', (timeToNextRetry) => {
        logInfo(`[Redis Lifecycle] Reconnecting to Redis server in ${timeToNextRetry}ms...`);
    });

    // Emitted when connection is permanently closed via quit() or disconnect()
    redisClient.on('end', () => {
        logInfo(`[Redis Lifecycle] Redis connection closed.`);
    });

    return redisClient;
}

/**
 * Healthcheck Probe Helper: Performs a `PING` command against Redis.
 * Meant for container liveness and readiness probes (`GET /health`).
 * 
 * @returns {Promise<{ status: string, latencyMs: number, host: string, error?: string }>}
 */
async function checkRedisHealth() {
    const client = getRedisClient();
    const startTime = Date.now();
    try {
        const pingResponse = await client.ping();
        const latencyMs = Date.now() - startTime;
        
        return {
            status: pingResponse === 'PONG' ? 'UP' : 'DOWN',
            latencyMs,
            host: config.redis.host
        };
    } catch (err) {
        return {
            status: 'DOWN',
            error: err.message,
            host: config.redis.host
        };
    }
}

/**
 * Graceful Shutdown Helper: Flushes pending queued commands and closes connection cleanly.
 */
async function closeRedisConnection() {
    if (redisClient) {
        logInfo('[Redis Cleanup] Closing Redis client connection gracefully...');
        await redisClient.quit();
        redisClient = null;
    }
}

module.exports = {
    getRedisClient,
    checkRedisHealth,
    closeRedisConnection
};
