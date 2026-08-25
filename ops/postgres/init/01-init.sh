#!/bin/bash
# Creates the extra databases Temporal needs, plus the pgvector extension on the Relay database.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
    CREATE EXTENSION IF NOT EXISTS vector;
SQL

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
        echo "Creating database '$db'"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
            CREATE DATABASE $db;
            GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
SQL
    done
fi
