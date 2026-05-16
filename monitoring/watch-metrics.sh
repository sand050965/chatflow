#!/bin/bash
# ==================================================
# Usage: run this script on local
# Refreshes metrics every 2 seconds during load test
# Consumer: 172.31.17.225:8081
# ==================================================

watch -n 2 'curl -s http://172.31.17.225:8081/metrics | python3 -m json.tool'