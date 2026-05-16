#!/bin/bash
# ==================================================
# Usage: run this script on the consumer EC2 instance
# Postgres : 172.31.8.77:5432
# Redis : 172.31.5.215
# RabbitMQ : 172.31.2.202:5672
# Consumer : 172.31.12.100:8081
# ==================================================

DB_URL="${DB_URL:-jdbc:postgresql://172.31.8.77:5432/chatflow}"
DB_USER="${DB_USER:-admin}"
DB_PASSWORD="${DB_PASSWORD:-admin}"

nohup java -jar ~/consumer-v3-0.0.1-SNAPSHOT.jar \
  --spring.rabbitmq.host=172.31.2.202 \
  --spring.rabbitmq.port=5672 \
  --spring.rabbitmq.username=admin \
  --spring.rabbitmq.password=admin \
  --spring.datasource.url="${DB_URL}" \
  --spring.datasource.username="${DB_USER}" \
  --spring.datasource.password="${DB_PASSWORD}" \
  --spring.data.redis.host=172.31.5.215 \
  --spring.data.redis.port=6379 \
  --db.writer.batch-size=${DB_BATCH_SIZE:-500} \
  --db.writer.flush-interval-ms=${DB_FLUSH_INTERVAL_MS:-100} \
  --consumer.thread-count=20 \
  > consumer.log 2>&1 &

echo $! > consumer.pid
