#!/bin/bash
# ==================================================
# Usage: run this script on local
# Refreshes metrics every 2 seconds during load test
# Consumer: localhost:8081
# ==================================================

watch -n 2 'curl -s http://localhost:8081/metrics | python3 -m json.tool'