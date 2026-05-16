#!/bin/bash
# =============================================
# Install Java 21 (Ubuntu)
# =============================================
# Usage: ./install-java.sh

set -e

echo "=== Setting ForceIPv4 ==="
echo 'Acquire::ForceIPv4 "true";' | sudo tee /etc/apt/apt.conf.d/99force-ipv4

echo "=== Installing Java 21 ==="
sudo apt update
sudo apt install -y openjdk-21-jdk

echo ""
echo "=== Java Version ==="
java -version