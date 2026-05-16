-- ============================================================
-- Materialized Views for Analytics
-- Refresh with: SELECT refresh_analytics_views();
-- ============================================================

-- Top users by message count (last 24h)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_top_users AS
SELECT user_id, COUNT(*) AS message_count
FROM messages
WHERE created_at >= now() - INTERVAL '24 hours'
GROUP BY user_id
ORDER BY message_count DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_top_users_user
    ON mv_top_users (user_id);

-- Top rooms by message count (last 24h)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_top_rooms AS
SELECT room_id, COUNT(*) AS message_count
FROM messages
WHERE created_at >= now() - INTERVAL '24 hours'
GROUP BY room_id
ORDER BY message_count DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_top_rooms_room
    ON mv_top_rooms (room_id);

-- Messages per minute (last 24h)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_messages_per_minute AS
SELECT date_trunc('minute', created_at) AS bucket, COUNT(*) AS message_count
FROM messages
WHERE created_at >= now() - INTERVAL '24 hours'
GROUP BY bucket
ORDER BY bucket;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_messages_per_minute_bucket
    ON mv_messages_per_minute (bucket);

-- Helper function to refresh all views concurrently
CREATE OR REPLACE FUNCTION refresh_analytics_views() RETURNS void AS $$
BEGIN
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_top_users;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_top_rooms;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_messages_per_minute;
END;
$$ LANGUAGE plpgsql;
