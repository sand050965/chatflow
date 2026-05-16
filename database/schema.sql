CREATE TABLE IF NOT EXISTS messages (
  message_id   VARCHAR(64) PRIMARY KEY,
  room_id      VARCHAR(64) NOT NULL,
  user_id      VARCHAR(64) NOT NULL,
  username     VARCHAR(128),
  message      TEXT,
  message_type VARCHAR(16) NOT NULL,
  server_id    VARCHAR(64),
  client_ip    VARCHAR(64),
  created_at   TIMESTAMPTZ NOT NULL,
  ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
