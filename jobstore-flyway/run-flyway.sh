#!/usr/bin/env bash

DB_NAME="job_scheduler_db"
DB_URL="jdbc:postgresql://localhost:5435/${DB_NAME}"
DB_USER="postgres"
DB_PASSWORD="postgres"

# Fail script on error
set -e

function runJobStoreFlyway() {
    echo "Running jobstore Flyway migrations..."

    flyway \
      -url="${DB_URL}" \
      -user="${DB_USER}" \
      -password="${DB_PASSWORD}" \
      -locations=filesystem:src/main/resources/db/migration \
      -baselineOnMigrate=true \
      migrate

    echo "Finished running jobstore Flyway migrations"
}

runJobStoreFlyway