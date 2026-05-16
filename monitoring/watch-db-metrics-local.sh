#!/bin/bash
set -euo pipefail

while true; do
  clear
  ./check-db-metrics-local.sh
  sleep 2
done
