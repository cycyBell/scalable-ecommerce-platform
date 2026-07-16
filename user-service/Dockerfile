# ==========================================
# Stage 1: Build & Compile (Build Environment)
# ==========================================
FROM eclipse-temurin:26-jdk-noble AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -B && \
    mv target/user-service-0.0.1-SNAPSHOT.jar app.jar

# ==========================================
# Stage 2: Runtime Execution (Production Environment)
# ==========================================
FROM eclipse-temurin:26-jre-noble AS runner
WORKDIR /app

# Install curl for the HEALTHCHECK below, then clean up apt caches in
# the SAME layer to avoid bloating the final image with package-manager
# metadata that's useless after installation.
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

COPY --from=builder /build/app.jar app.jar

EXPOSE 8080

# Docker periodically runs this INSIDE the container to determine
# whether it's actually healthy — not just "the process exists," but
# "the app is genuinely serving traffic and its dependencies (DB, Redis)
# are reachable," since that's exactly what /actuator/health reports.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-XX:+UseG1GC", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-jar", \
            "app.jar"]