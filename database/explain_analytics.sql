-- ============================================================
-- Analytics Query Profiling
-- Run after seeding representative data into messages.
-- Optional: set a narrower or wider time window by editing the
-- timestamps below before running this file.
-- ============================================================

\echo 'Baseline raw-table query: messages per minute (last 24h)'
EXPLAIN ANALYZE
SELECT date_trunc('minute', created_at) AS bucket, COUNT(*) AS cnt
FROM messages
WHERE created_at BETWEEN now() - INTERVAL '24 hours' AND now()
GROUP BY bucket
ORDER BY bucket;

\echo 'Materialized view query: messages per minute (last 24h)'
EXPLAIN ANALYZE
SELECT bucket, message_count AS cnt
FROM mv_messages_per_minute
WHERE bucket BETWEEN date_trunc('minute', now() - INTERVAL '24 hours')
    AND date_trunc('minute', now())
ORDER BY bucket;

\echo 'Baseline raw-table query: top users (last 24h)'
EXPLAIN ANALYZE
SELECT user_id AS id, COUNT(*) AS cnt
FROM messages
WHERE created_at BETWEEN now() - INTERVAL '24 hours' AND now()
GROUP BY user_id
ORDER BY cnt DESC
LIMIT 10;

\echo 'Materialized view query: top users (last 24h)'
EXPLAIN ANALYZE
SELECT user_id AS id, message_count AS cnt
FROM mv_top_users
ORDER BY message_count DESC, user_id
LIMIT 10;

\echo 'Baseline raw-table query: top rooms (last 24h)'
EXPLAIN ANALYZE
SELECT room_id AS id, COUNT(*) AS cnt
FROM messages
WHERE created_at BETWEEN now() - INTERVAL '24 hours' AND now()
GROUP BY room_id
ORDER BY cnt DESC
LIMIT 10;

\echo 'Materialized view query: top rooms (last 24h)'
EXPLAIN ANALYZE
SELECT room_id AS id, message_count AS cnt
FROM mv_top_rooms
ORDER BY message_count DESC, room_id
LIMIT 10;
