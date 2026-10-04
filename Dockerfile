# ============================================================
# Stage 1: Builder
#   Uses full JDK + Maven to compile and package the fat JAR.
#   This stage is discarded — its artifacts don't reach prod.
# ============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first to leverage Docker layer caching.
# If pom.xml is unchanged, this layer is reused and dependencies
# are NOT re-downloaded on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Pre-download all dependencies into the cache layer
RUN ./mvnw dependency:go-offline -B

# Copy source and build the fat JAR (skip tests — tests run in CI)
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 2: Runner
#   Minimal JRE-only image — no JDK, no Maven, no source code.
#   Final image size: ~180MB vs ~600MB for a single-stage build.
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runner

# Create a non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the builder stage
COPY --from=builder /build/target/*.jar app.jar

# Ensure the non-root user owns the app directory
RUN chown -R appuser:appgroup /app

USER appuser

# Expose application port
EXPOSE 8080

# JVM tuning flags:
#   -XX:+UseContainerSupport      → JVM reads cgroup memory limits (not host RAM)
#   -XX:MaxRAMPercentage=75.0     → Use up to 75% of container RAM for heap;
#                                   reserves 25% for OS + off-heap (Netty, Metaspace)
#   -XX:+UseG1GC                  → G1 garbage collector: best balance of
#                                   throughput and low pause times
#   -Djava.security.egd=...       → Faster SecureRandom init (critical for JWT signing)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
