#!/bin/bash
# ==================================================
# Usage: run this script on local
# Prints a one-time snapshot of consumer metrics
# Consumer: 172.31.17.225:8081
# ==================================================

curl -s http://172.31.17.225:8081/metrics | python3 -m json.tool