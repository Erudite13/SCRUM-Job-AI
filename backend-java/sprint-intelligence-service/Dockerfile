# ==============================================================================
# Sprint Intelligence Service - Production Dockerfile
# Multi-stage build: Maven + JDK 21 -> JRE 21 Alpine Runtime
# ==============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy parent POM first for dependency caching
COPY ../pom.xml /build/parent-pom.xml

# Copy module POM and download dependencies (cache layer)
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -f pom.xml 2>/dev/null || true

# Copy source code
COPY src ./src

# Build the application JAR (skip tests — they run in CI)
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B && \
    mv target/*.jar target/app.jar

# Extract Spring Boot layers for optimized Docker caching
RUN java -Djarmode=layertools -jar target/app.jar extract --destination /extracted

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: add curl for healthcheck, ca-certs for TLS
RUN apk add --no-cache curl ca-certificates tzdata && \
    rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup -h /app -s /sbin/nologin

WORKDIR /app

# Copy Spring Boot layers in dependency order (most stable first)
COPY --from=builder --chown=appuser:appgroup /extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /extracted/application/ ./

# Switch to non-root user
USER appuser:appgroup

# Expose service port
EXPOSE 8081

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Health check: Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8081/actuator/health || exit 1

# Launch with Spring Boot launcher for layered JARs
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

LABEL maintainer="AI Scrum Master Platform Team" \
      app.name="sprint-intelligence-service" \
      app.version="1.0.0" \
      app.port="8081"
