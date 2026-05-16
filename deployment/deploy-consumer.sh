#!/bin/bash
# ============================================================
# Consumer Deploy Script
# Usage: ./deploy-consumer.sh <EC2_PUBLIC_IP>
# ============================================================

set -e

EC2_IP=$1
KEY_FILE="~/.ssh/aws.pem"
JAR_FILE="consumer/build/libs/consumer-0.0.1-SNAPSHOT.jar"
SERVER_1_IP="<SERVER_1_IP>"
SERVER_2_IP="<SERVER_2_IP>"

if [ -z "$EC2_IP" ]; then
  echo "Usage: ./deploy-consumer.sh <EC2_IP>"
  exit 1
fi

echo "=== Deploying Consumer to $EC2_IP ==="

echo "--- Building JAR ---"
cd consumer
./gradlew bootJar
cd ..

echo "--- Copying JAR to EC2 ---"
scp -i $KEY_FILE $JAR_FILE ec2-user@$EC2_IP:~/consumer.jar

echo "--- Creating start script ---"
cat > /tmp/start-consumer.sh << EOF
#!/bin/bash
pkill -f 'consumer.jar' || true
sleep 2
nohup java -jar ~/consumer.jar \
  --server.port=8081 \
  --spring.rabbitmq.host=<RABBITMQ_IP> \
  --broadcast.server-urls=http://$SERVER_1_IP:8080,http://$SERVER_2_IP:8080 \
  --consumer.thread-count=20 \
  --consumer.prefetch=10 \
  > ~/consumer.log 2>&1 &
echo "Consumer started, PID: \$!"
EOF

scp -i $KEY_FILE /tmp/start-consumer.sh ec2-user@$EC2_IP:~/start-consumer.sh
ssh -i $KEY_FILE ec2-user@$EC2_IP "chmod +x ~/start-consumer.sh && ~/start-consumer.sh"

echo "=== Consumer deployed to $EC2_IP ==="
echo "Check logs: ssh -i $KEY_FILE ec2-user@$EC2_IP 'tail -f ~/consumer.log'"