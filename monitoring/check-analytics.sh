#!/bin/bash
# ==================================================
# Usage: run this script on local
# Calls the Metrics Analytics API and prints results
# Consumer: 172.31.17.225:8081
# Args (optional):
#   $1 = roomId   (default: 1)
#   $2 = userId   (default: 1)
#   $3 = startTime (default: today 00:00:00Z)
#   $4 = endTime   (default: today 23:59:59Z)
#   $5 = topN      (default: 5)
#   $6 = limit     (default: 1000)
# ==================================================

ROOM_ID=${1:-1}
USER_ID=${2:-1}
START_TIME=${3:-$(date -u +%Y-%m-%dT00:00:00Z)}
END_TIME=${4:-$(date -u +%Y-%m-%dT23:59:59Z)}
TOP_N=${5:-5}
LIMIT=${6:-1000}

echo "============================================================"
echo "  METRICS ANALYTICS API"
echo "============================================================"
echo "  roomId    : $ROOM_ID"
echo "  userId    : $USER_ID"
echo "  startTime : $START_TIME"
echo "  endTime   : $END_TIME"
echo "  topN      : $TOP_N"
echo "  limit     : $LIMIT"
echo "============================================================"

curl -s "http://172.31.17.225:8081/metrics/analytics?roomId=${ROOM_ID}&userId=${USER_ID}&startTime=${START_TIME}&endTime=${END_TIME}&topN=${TOP_N}&limit=${LIMIT}" \
  | python3 -m json.tool
