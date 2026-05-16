CREATE INDEX IF NOT EXISTS idx_messages_room_time
  ON messages (room_id, created_at);

CREATE INDEX IF NOT EXISTS idx_messages_user_time
  ON messages (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_messages_time_user
  ON messages (created_at, user_id);

CREATE INDEX IF NOT EXISTS idx_messages_user_room_time
  ON messages (user_id, room_id, created_at DESC);
