# ============================================================
# Stage 1: Builder
#   Uses Maven on Alpine to compile and package the fat JAR.
#   Leverages Docker layer caching for dependencies.
# ============================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy pom.xml and cache dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build fat JAR (skip unit/integration tests during image build)
COPY src ./src
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2: Runner
#   Minimal JRE-only image — no JDK, no Maven, no source code.
#   Final image size: ~180MB.
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
#   -XX:+UseContainerSupport      → JVM reads cgroup memory limits
#   -XX:MaxRAMPercentage=75.0     → Use up to 75% of container RAM for heap
#   -XX:+UseG1GC                  → G1 garbage collector for low latency
#   -XX:MaxGCPauseMillis=200      → Target GC pause time
#   -Djava.security.egd=...       → Faster SecureRandom init for JWT
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
