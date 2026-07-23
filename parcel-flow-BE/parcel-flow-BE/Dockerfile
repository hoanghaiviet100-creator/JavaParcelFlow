# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Cache dependencies first
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
# Build the app
COPY src ./src
RUN mvn -q -e -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# curl is only needed for the container HEALTHCHECK.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user rather than root. A container breakout from a
# root process lands as root on the host user namespace; a non-root process
# does not. The jar is owned by root and only read, so the app cannot rewrite
# its own binary.
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080

# Liveness probe. Points at the readiness group (DB/Redis), NOT plain
# /actuator/health, whose aggregate includes the mail indicator — a transient
# SMTP outage must not report the API itself as unhealthy and take it out of
# rotation. See management.endpoint.health.group.readiness in application.yml.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=10 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

# Container-aware memory + a fixed timezone so log timestamps are stable.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=UTC", "-jar", "app.jar"]
