#!/bin/bash
# ==================================================
# Usage: run this script on the respective EC2 instance
# RabbitMQ : 172.31.2.202
# Redis : 172.31.5.215
# Consumer : 172.31.12.100
# Server1  : 172.31.39.145
# Server2  : 172.31.39.148
# Server3  : 172.31.9.40
# Server4  : 172.31.9.32
# ==================================================


# ==================================================
# Server2 — run on 172.31.39.148
# ==================================================
nohup java -jar ~/server-v2-0.0.1-SNAPSHOT.jar \
  --spring.rabbitmq.host=172.31.2.202 \
  --spring.rabbitmq.port=5672 \
  --spring.rabbitmq.username=admin \
  --spring.rabbitmq.password=admin \
  --spring.data.redis.host=172.31.5.215 \
  --spring.data.redis.port=6379 \
  --server.url=http://172.31.39.148:8080 \
  --consumer.url=http://172.31.12.100:8081 \
  --rabbitmq.pool.connections=2 \
  --rabbitmq.pool.channels-per-connection=25 \
  > server.log 2>&1 &

echo $! > server.pid