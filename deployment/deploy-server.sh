#!/bin/bash
# =============================================
# Start WebSocket Chat Server (server-v2)
# =============================================
# Usage: ./start-server.sh
# Run this on each WebSocket EC2 instance

set -e

JAR_PATH="./server-v2/target/server-v2.jar"
SERVER_PORT=8080
SERVER_ID=$(hostname)

# RabbitMQ
RABBITMQ_HOST="<rabbitmq-ec2-private-ip>"
RABBITMQ_PORT=5672
RABBITMQ_USER="admin"
RABBITMQ_PASS="guess"
RABBITMQ_EXCHANGE="chat.exchange"

# Connection pool
CHANNEL_POOL_SIZE=20
# ------------------------------------

echo "=== Starting Chat Server ==="
echo "Server ID:       $SERVER_ID"
echo "Port:            $SERVER_PORT"
echo "RabbitMQ Host:   $RABBITMQ_HOST"

java -jar $JAR_PATH \
  --server.port=$SERVER_PORT \
  --server.id=$SERVER_ID \
  --rabbitmq.host=$RABBITMQ_HOST \
  --rabbitmq.port=$RABBITMQ_PORT \
  --rabbitmq.username=$RABBITMQ_USER \
  --rabbitmq.password=$RABBITMQ_PASS \
  --rabbitmq.exchange=$RABBITMQ_EXCHANGE \
  --rabbitmq.channel-pool-size=$CHANNEL_POOL_SIZE \
  &

echo "Server started. PID: $!"
echo $! > server.pid
echo "PID saved to server.pid"