# ==============================================================================
# Frontend - Production Dockerfile
# Nginx Alpine serving static Angular/HTML build
# ==============================================================================

FROM nginx:alpine AS runtime

# Remove default nginx config and html
RUN rm -rf /etc/nginx/conf.d/default.conf /usr/share/nginx/html/*

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup -s /sbin/nologin

# Copy custom nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy built static assets
COPY dist/ /usr/share/nginx/html/

# Fix ownership for non-root
RUN chown -R appuser:appgroup /usr/share/nginx/html && \
    chown -R appuser:appgroup /var/cache/nginx && \
    chown -R appuser:appgroup /var/log/nginx && \
    touch /tmp/nginx.pid && \
    chown appuser:appgroup /tmp/nginx.pid

# Switch to non-root user
USER appuser:appgroup

# Expose HTTP port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -sf http://localhost:80/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]

LABEL maintainer="AI Scrum Master Platform Team" \
      app.name="frontend" \
      app.version="1.0.0" \
      app.port="80"
