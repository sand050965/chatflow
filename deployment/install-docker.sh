#!/bin/bash
# =============================================
# Install Docker + Docker Compose (Ubuntu)
# =============================================
# Usage: ./install-docker.sh
# Run this on your RabbitMQ EC2 instance

set -e

echo "=== Installing Docker ==="
sudo apt update -y
sudo apt install -y docker.io

echo "=== Starting Docker Service ==="
sudo systemctl start docker
sudo systemctl enable docker

echo "=== Adding current user to docker group (no sudo needed) ==="
sudo usermod -aG docker $USER

echo "=== Installing Docker Compose ==="
COMPOSE_VERSION="v2.24.6"
sudo curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

echo ""
echo "=== Versions ==="
docker --version
docker-compose --version

echo ""
echo "=== Done! ==="
echo "NOTE: Please run 'newgrp docker' or re-login for group changes to take effect."