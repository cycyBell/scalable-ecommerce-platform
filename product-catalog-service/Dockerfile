FROM python:3.12-slim

WORKDIR /app

# Copy ONLY requirements.txt first — same layer-caching principle from
# User Service's Dockerfile: if requirements.txt hasn't changed,
# Docker reuses the cached "pip install" layer instead of re-downloading
# every dependency on every rebuild.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the actual application code. During active development, "watch"
# will sync changes here directly into the running container — this
# COPY only matters for the INITIAL build and for any change requiring
# a full rebuild (e.g. requirements.txt changing).
COPY app ./app

EXPOSE 8000

# --reload here mirrors what you've been running locally via uvicorn
# directly — it's genuinely fine to use --reload inside a dev-purpose
# Dockerfile like this one. (We'll build a separate, production-hardened
# Dockerfile — multi-stage, no --reload, non-root user — at the actual
# containerization phase, same as we did for User Service at Step 13.)
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]