# ==============================================================================
# MULTI-STAGE DOCKERFILE: Shopping Cart Microservice
# ==============================================================================
# 
# WHY MULTI-STAGE BUILDS FOR NODE.JS?
# 1. Image Size Optimization: Development dependencies, build toolchains (like gcc,
#    make, python), and npm caches are discarded in the final production image.
# 2. Attack Surface Reduction: The final image contains only production node_modules
#    and runtime source code.
# 3. Security Principle of Least Privilege: The container executes under the built-in
#    unprivileged `node` user rather than `root`.
# ==============================================================================

# ------------------------------------------------------------------------------
# STAGE 1: Dependency Installation & Build Layer
# ------------------------------------------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package descriptors first to leverage Docker layer caching
COPY package.json package-lock.json ./

# Install only production dependencies cleanly
RUN npm ci --omit=dev --ignore-scripts

# ------------------------------------------------------------------------------
# STAGE 2: Production Minimal Runtime Image
# ------------------------------------------------------------------------------
FROM node:20-alpine AS runner

WORKDIR /app

# Set production environment flags
ENV NODE_ENV=production
ENV PORT=8002

# Copy production node_modules from the builder stage
COPY --from=builder /app/node_modules ./node_modules

# Copy application source code and descriptors
COPY package.json ./
COPY src/ ./src/

# Assign ownership of the /app directory to the built-in non-root `node` user
RUN chown -R node:node /app

# Drop root privileges and switch to the unprivileged `node` user (UID 1000)
USER node

# Expose microservice HTTP port
EXPOSE 8002

# Container Healthcheck Probe: Polls the /health endpoint every 30 seconds
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8002/health || exit 1

# Start the Node.js application process
CMD ["node", "src/index.js"]
