#!/bin/bash
# ==================================================
# Usage: run this script on the respective EC2 instance
# ==================================================

if [ -f server.pid ]; then
  PID=$(cat server.pid)
  if kill -0 $PID 2>/dev/null; then
    kill $PID
    echo "Server stopped (pid=$PID)"
  else
    echo "Process $PID not running"
  fi
  rm -f server.pid
else
  echo "server.pid not found"
fi