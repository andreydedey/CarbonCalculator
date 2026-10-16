# ── Stage 1: Build frontend ──────────────────────────────────────────
FROM oven/bun:1 AS frontend-build
WORKDIR /app/client
COPY client/package.json client/bun.lock ./
RUN bun install --frozen-lockfile
COPY client/ .
RUN bun run build

# ── Stage 2: Build backend ──────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS backend-build
WORKDIR /app/server
COPY server/.mvn .mvn
COPY server/mvnw server/pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY server/src src
RUN ./mvnw package -DskipTests -B

# ── Stage 3: Runtime ────────────────────────────────────────────────
FROM eclipse-temurin:25-jre

RUN apt-get update && apt-get install -y --no-install-recommends nginx gettext-base \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=backend-build /app/server/target/*.jar app.jar
COPY --from=frontend-build /app/client/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/start.sh /app/start.sh
RUN chmod +x /app/start.sh

EXPOSE 8080

CMD ["/app/start.sh"]
