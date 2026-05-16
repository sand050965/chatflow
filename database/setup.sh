#!/bin/bash
set -euo pipefail

DB_NAME="${DB_NAME:-chatflow}"
DB_USER="${DB_USER:-chatflow}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

psql "postgresql://${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}" -f "$(dirname "$0")/schema.sql"
psql "postgresql://${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}" -f "$(dirname "$0")/indexes.sql"

echo "Schema applied to ${DB_NAME}"
