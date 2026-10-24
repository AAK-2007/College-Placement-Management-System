# Production image for the placement web app (used by Render, and by any Docker host).
# Build:  docker build -t placement-web .
# Run:    docker run -p 8080:8080 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... -e OFFICER_PASSWORD_HASH=... placement-web

# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Download dependencies first so they are cached between builds.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
# Tests run on your PC (.\mvnw.cmd test); skip them here to keep the cloud build small and fast.
RUN mvn -q -B -DskipTests package

# ---- Stage 2: run it on a small Java runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as a normal user, not root.
RUN useradd --system --create-home appuser
COPY --from=build /build/target/placement-web-1.0.0.jar app.jar
USER appuser

# Production settings, given as environment variables (they override application.properties):
#  - behind the host's https proxy, build links and redirects with https
#  - cache page templates instead of re-reading them on every request
#  - let Java use most of the container's memory (free plans have about 512 MB)
ENV SERVER_FORWARD_HEADERS_STRATEGY=framework \
    SPRING_THYMELEAF_CACHE=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
# Listen on all network interfaces, on the port the host provides in $PORT (8080 if none).
CMD ["sh", "-c", "exec java -jar app.jar --server.address=0.0.0.0 --server.port=${PORT:-8080}"]
