#!/bin/bash
set -euo pipefail

DB_NAME="${DB_NAME:-chatflow}"
DB_USER="${DB_USER:-chatflow}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [[ -n "${DB_PASSWORD}" ]]; then
  export PGPASSWORD="${DB_PASSWORD}"
fi

psql "postgresql://${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}" -c "
SELECT
  now() AS ts,
  datname AS db,
  numbackends AS active_connections,
  xact_commit + xact_rollback AS total_tx,
  blks_read,
  blks_hit,
  ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS buffer_hit_ratio
FROM pg_stat_database
WHERE datname = '${DB_NAME}';
"

psql "postgresql://${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}" -c "
SELECT
  count(*) AS locks,
  mode
FROM pg_locks
GROUP BY mode
ORDER BY locks DESC;
"
