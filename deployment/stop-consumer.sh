#!/bin/bash
# ==================================================
# Usage: run this script on the consumer EC2 instance
# Consumer: 172.31.17.225
# ==================================================

if [ -f consumer.pid ]; then
  PID=$(cat consumer.pid)
  if kill -0 $PID 2>/dev/null; then
    kill $PID
    echo "Consumer stopped (pid=$PID)"
  else
    echo "Process $PID not running"
  fi
  rm -f consumer.pid
else
  echo "consumer.pid not found"
fi