# ==============================================================================
# DSM Voice Engine - Production Dockerfile
# Python 3.12-slim WebSocket server with non-root user
# ==============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Dependencies
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS dependencies

WORKDIR /build

RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc && \
    rm -rf /var/lib/apt/lists/*

# Copy and install dependencies (voice engine + shared if exists)
COPY requirements.txt* ./
RUN if [ -f requirements.txt ]; then \
        pip install --no-cache-dir --prefix=/install -r requirements.txt; \
    else \
        pip install --no-cache-dir --prefix=/install websockets aiohttp; \
    fi

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS runtime

# Install runtime deps
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m -s /sbin/nologin appuser

# Copy installed Python packages from build stage
COPY --from=dependencies /install /usr/local

WORKDIR /app

# Copy voice engine application code
COPY --chown=appuser:appgroup voice_dsm_moderator.py ./
COPY --chown=appuser:appgroup *.py ./
COPY --chown=appuser:appgroup *.json ./
COPY --chown=appuser:appgroup *.yaml ./

# Switch to non-root user
USER appuser:appgroup

# Expose WebSocket port
EXPOSE 8001

# Environment configuration
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# Health check via TCP (WebSocket server)
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import socket; s=socket.socket(); s.settimeout(3); s.connect(('127.0.0.1',8001)); s.close()" || exit 1

# Launch voice engine
CMD ["python", "voice_dsm_moderator.py"]

LABEL maintainer="AI Scrum Master Platform Team" \
      app.name="dsm-voice-engine" \
      app.version="1.0.0" \
      app.port="8001"
