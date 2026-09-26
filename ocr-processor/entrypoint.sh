#!/usr/bin/env bash
set -e

echo "============================================================"
echo " Starting OCR Processor on Hugging Face Spaces"
echo "============================================================"

# Hugging Face Spaces routes web traffic to port 7860
export PORT="${PORT:-7860}"
export SERVER_PORT="${PORT}"

# Database initialization
if [ -n "$SPRING_DATASOURCE_URL" ] && [[ "$SPRING_DATASOURCE_URL" != *"localhost"* ]] && [[ "$SPRING_DATASOURCE_URL" != *"127.0.0.1"* ]]; then
    echo "[Database] Connecting to external PostgreSQL configured via SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}"
else
    echo "[Database] Initializing embedded PostgreSQL instance..."
    export PGDATA="/home/user/pgdata"

    if [ ! -d "$PGDATA" ]; then
        echo "[Database] Running initdb for user postgres..."
        initdb -D "$PGDATA" -U postgres --auth=trust
    fi

    echo "[Database] Starting PostgreSQL background daemon on 127.0.0.1:5432..."
    pg_ctl -D "$PGDATA" -l /home/user/postgres.log -o "-p 5432 -k /tmp -h 127.0.0.1" start

    # Wait for PostgreSQL to accept connections
    echo "[Database] Waiting for PostgreSQL readiness..."
    for i in {1..30}; do
        if pg_isready -h 127.0.0.1 -p 5432 -U postgres >/dev/null 2>&1; then
            echo "[Database] PostgreSQL is ready and accepting connections."
            break
        fi
        sleep 1
    done

    # Ensure database exists
    psql -h 127.0.0.1 -p 5432 -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'ocrprocessor'" | grep -q 1 || \
        psql -h 127.0.0.1 -p 5432 -U postgres -c "CREATE DATABASE ocrprocessor;"

    export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/ocrprocessor"
    export SPRING_DATASOURCE_USERNAME="postgres"
    export SPRING_DATASOURCE_PASSWORD="postgres"
fi

# Ensure fallback JWT secret if not configured in Space Secrets
if [ -z "$JWT_SECRET" ]; then
    export JWT_SECRET="huggingface-spaces-jwt-secret-key-that-is-at-least-32-chars!"
fi

echo "============================================================"
echo " Launching Spring Boot Application on port ${SERVER_PORT}"
echo "============================================================"
exec java -jar app.jar --server.port="${SERVER_PORT}"
