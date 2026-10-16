#!/usr/bin/env sh
set -e

# Default to 8080 if PORT is not set (Railway injects PORT)
PORT="${PORT:-8080}"
export PORT

# Resolve $PORT inside nginx.conf
envsubst '${PORT}' < /etc/nginx/nginx.conf > /tmp/nginx.conf
cp /tmp/nginx.conf /etc/nginx/nginx.conf

# Activate prod profile
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

# Start Spring Boot on internal port 8081
java -jar /app/app.jar --server.port=8081 &

# Start nginx in the foreground
exec nginx -g 'daemon off;'
