#!/bin/bash
# ==================================================
# Usage: run this script on local
# Prints a one-time snapshot of consumer metrics
# Consumer: localhost:8081
# ==================================================

curl -s http://localhost:8081/metrics | python3 -m json.tool