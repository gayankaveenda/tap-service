#!/bin/bash

echo "Starting tap-service..."

# Start Docker containers
docker compose up -d

# Wait for PostgreSQL to be ready
echo "Waiting for PostgreSQL to be ready..."
until docker exec pg17-dev pg_isready -U devuser -d devdb > /dev/null 2>&1; do
    sleep 1
done
echo "PostgreSQL is ready."

# Create schema
echo "Creating schema..."
docker exec pg17-dev psql -U devuser -d devdb -c 'CREATE SCHEMA IF NOT EXISTS "tap-service";'

echo "Done. Run ./gradlew bootRun to start the application."
