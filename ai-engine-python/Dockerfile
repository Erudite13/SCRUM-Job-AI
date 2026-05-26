# ==============================================================================
# AI Engine (FastAPI) - Production Dockerfile
# Python 3.12-slim with non-root user and health checks
# ==============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Dependencies
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS dependencies

WORKDIR /build

# Install system-level build deps required by some Python packages
RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc libpq-dev && \
    rm -rf /var/lib/apt/lists/*

# Copy and install Python dependencies (cache layer)
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS runtime

# Install runtime-only system deps
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl libpq5 ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m -s /sbin/nologin appuser

# Copy installed Python packages from build stage
COPY --from=dependencies /install /usr/local

WORKDIR /app

# Copy application source code
COPY --chown=appuser:appgroup fastapi-app/ ./fastapi-app/
COPY --chown=appuser:appgroup langgraph-agents/ ./langgraph-agents/
COPY --chown=appuser:appgroup rag-memory-pipeline/ ./rag-memory-pipeline/

# Switch to non-root user
USER appuser:appgroup

# Expose service port
EXPOSE 8000

# Environment configuration
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -sf http://localhost:8000/healthz || exit 1

# Launch FastAPI with uvicorn (production workers)
CMD ["uvicorn", "fastapi-app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4", "--loop", "uvloop", "--http", "httptools"]

LABEL maintainer="AI Scrum Master Platform Team" \
      app.name="ai-engine" \
      app.version="1.0.0" \
      app.port="8000"
