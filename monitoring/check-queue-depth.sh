#!/bin/bash
# ==================================================
# Usage: run this script on local
# Calls the metrics summary API including queue depth timeline
# Consumer: 172.31.17.225:8081
# ==================================================

echo "============================================================"
echo "  QUEUE DEPTH SUMMARY"
echo "============================================================"

curl -s http://172.31.17.225:8081/metrics/summary | python3 -m json.tool
