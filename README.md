# Group ChatFlow — Distributed Chat System

A high-throughput real-time chat system built for CS6650 (Distributed Systems). The architecture is designed for horizontal scalability and optimized analytics via PostgreSQL materialized views, Redis caching, and asynchronous message processing through RabbitMQ.

## Architecture Overview

```
Clients (WebSocket)
        │
        ▼
AWS Application Load Balancer (HTTP :80, sticky sessions)
        │
   ┌────┴────┐
   │         │  (round-robin across 4 instances)
Server-v2 ×4 (Spring Boot, :8080)
   │    │
   │    └──── Redis (session state, pub-sub, analytics cache)
   │
RabbitMQ (:5672)
   │
Consumer-v3 (Spring Boot, :8081)
   │    │
   │    └──── Redis (analytics cache)
   │
PostgreSQL (:5432)
   │
   ├── messages table (indexes: room_time, user_time, user_room_time)
   └── Materialized views (mv_top_users, mv_top_rooms, mv_messages_per_minute)
```

**Services:**
| Service | Role | Port |
|---|---|---|
| server-v2 | WebSocket server — accepts chat connections, produces to RabbitMQ | 8080 |
| consumer-v3 | Message consumer — batch-writes to PostgreSQL, serves analytics API | 8081 |
| PostgreSQL | Persistent message store with optimized analytics queries | 5432 |
| RabbitMQ | Async message queue decoupling servers from DB writes | 5672 / 15672 |
| Redis | Session state, cross-server pub-sub, analytics result caching | 6379 |

---

## Local Development

### Prerequisites
- Java 21
- Gradle
- Docker & Docker Compose

---

### Step 1: Start Infrastructure (RabbitMQ + PostgreSQL + Redis)

```bash
cd deployment
docker-compose up -d
```

| Service | URL / Address | Credentials |
|---|---|---|
| RabbitMQ Management | http://localhost:15672 | admin / admin |
| PostgreSQL | localhost:5432 | admin / admin, db: chatflow |
| Redis | localhost:6379 | — |

Initialize the database schema and indexes:
```bash
cd database
./setup.sh
```

---

### Step 2: Start Server

```bash
cd server-v2
./gradlew clean build -x test
./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 \
          --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin \
          --spring.redis.host=localhost --spring.redis.port=6379 \
          --server.url=http://localhost:8080 --consumer.url=http://localhost:8081'
```

Verify: http://localhost:8080/health

---

### Step 3: Start Consumer (v3)

```bash
cd consumer-v3
./gradlew clean build -x test
./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 \
          --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin \
          --spring.redis.host=localhost --spring.redis.port=6379 \
          --consumer.thread-count=40'
```

Verify: http://localhost:8081/health  
Analytics API: http://localhost:8081/metrics/analytics

---

### Step 4: Build & Run Load Test Client

```bash
cd client
./gradlew clean build -x test
java -jar build/libs/client-0.1.0.jar
```

Results are saved to `client/results/`.  
Metrics API response is saved to `client/results/metrics_api_response.json`.

Load test configs (thread count, message count, room/user distribution) are in `load-tests/configs/`:
- `baseline.json` — 500k messages, 128 threads, 20 rooms, 100k users
- `stress.json` — 1M messages, 256 threads
- `batch-size-tests.json` — batch size parameter sweep
- `endurance.json` — 15-minute sustained run at 80% throughput

---

### Monitoring

```bash
cd monitoring

# one-time snapshot
./check-metrics-local.sh

# live (requires: brew install watch)
./watch-metrics-local.sh

# analytics-specific
./check-analytics.sh

# RabbitMQ queue depth
./check-queue-depth.sh

# database metrics
./check-db-metrics-local.sh
```

---

### Query Profiling

Compare raw-table analytics queries against materialized-view-backed versions:

```bash
cd database
psql "postgresql://admin:admin@localhost:5432/chatflow" -f explain_analytics.sql
```

This profiles: messages per minute, top users, and top rooms over the last 24 hours.  
Compare execution time, scanned rows, and whether the planner uses indexes or pre-aggregated views.

For a repeatable before/after measurement process, see `database/measurement_guide.md`.

---

### Stop Services

Press `Ctrl+C` in each terminal to stop the server and consumer.

```bash
cd deployment
docker-compose down -v
```

---

## EC2 Deployment

### Infrastructure

| Instance | Role | Count |
|---|---|---|
| Server EC2 | server-v2 on :8080 | 4 |
| Consumer EC2 | consumer-v3 on :8081 | 1 |
| PostgreSQL EC2 | PostgreSQL on :5432 | 1 |
| RabbitMQ EC2 | RabbitMQ on :5672 | 1 |
| Redis EC2 | Redis on :6379 | 1 |
| AWS ALB | HTTP :80 → servers :8080, sticky sessions, `/health` check | 1 |

ALB DNS: `chat-servers-1288018322.us-west-2.elb.amazonaws.com`

### Prerequisites

Replace the following placeholders throughout:
- `<path-to-your-pem-key>` — path to your EC2 key file
- `<Server1-Public-IP>` through `<Server4-Public-IP>` — 4 server instances
- `<Consumer-Public-IP>` — consumer instance
- `<Postgres-Public-IP>` — PostgreSQL instance
- `<RabbitMQ-Public-IP>` — RabbitMQ instance
- `<Redis-Public-IP>` — Redis instance

---

### Step 1: Deploy RabbitMQ and PostgreSQL

```bash
# RabbitMQ
scp -i <path-to-your-pem-key> deployment/install-docker.sh deployment/docker-compose.yml ubuntu@<RabbitMQ-Public-IP>:~/
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "chmod +x install-docker.sh && ./install-docker.sh"
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "docker-compose up rabbitmq -d"

# PostgreSQL
scp -i <path-to-your-pem-key> deployment/install-docker.sh deployment/docker-compose.yml ubuntu@<Postgres-Public-IP>:~/
ssh -i <path-to-your-pem-key> ubuntu@<Postgres-Public-IP> "chmod +x install-docker.sh && ./install-docker.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Postgres-Public-IP> "docker-compose up postgres -d"
```

Initialize the database schema:
```bash
scp -i <path-to-your-pem-key> database/setup.sh database/schema.sql database/indexes.sql database/materialized_views.sql ubuntu@<Postgres-Public-IP>:~/
ssh -i <path-to-your-pem-key> ubuntu@<Postgres-Public-IP> "chmod +x setup.sh && ./setup.sh"
```

---

### Step 2: Deploy Redis

```bash
scp -i <path-to-your-pem-key> deployment/install-docker.sh deployment/docker-compose.yml ubuntu@<Redis-Public-IP>:~/
ssh -i <path-to-your-pem-key> ubuntu@<Redis-Public-IP> "chmod +x install-docker.sh && ./install-docker.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Redis-Public-IP> "docker-compose up redis -d"
```

---

### Step 3: Build & Deploy Servers

```bash
cd server-v2
./gradlew clean build -x test
cd ..

# Repeat for Server 1–4, substituting the server number and IP
for N in 1 2 3 4; do
  scp -i <path-to-your-pem-key> server-v2/build/libs/server-v2-0.0.1-SNAPSHOT.jar ubuntu@<ServerN-Public-IP>:~/
  scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-server${N}.sh deployment/stop-server.sh ubuntu@<ServerN-Public-IP>:~/
  ssh -i <path-to-your-pem-key> ubuntu@<ServerN-Public-IP> "chmod +x install-java.sh start-server${N}.sh stop-server.sh && ./install-java.sh"
  ssh -i <path-to-your-pem-key> ubuntu@<ServerN-Public-IP> "./start-server${N}.sh"
done
```

---

### Step 4: Build & Deploy Consumer

```bash
cd consumer-v3
./gradlew clean build -x test
cd ..

scp -i <path-to-your-pem-key> consumer-v3/build/libs/consumer-v3-0.0.1-SNAPSHOT.jar ubuntu@<Consumer-Public-IP>:~/
scp -i <path-to-your-pem-key> deployment/install-java.sh deployment/start-consumer.sh deployment/stop-consumer.sh ubuntu@<Consumer-Public-IP>:~/
ssh -i <path-to-your-pem-key> ubuntu@<Consumer-Public-IP> "chmod +x install-java.sh start-consumer.sh stop-consumer.sh && ./install-java.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Consumer-Public-IP> "./start-consumer.sh"
```

---

### Step 5: Verify

```bash
curl http://<Server1-Public-IP>:8080/health
curl http://<Server2-Public-IP>:8080/health
curl http://<Server3-Public-IP>:8080/health
curl http://<Server4-Public-IP>:8080/health
curl http://<Consumer-Public-IP>:8081/health
```

| Service | Connection |
|---|---|
| PostgreSQL | `jdbc:postgresql://<Postgres-Public-IP>:5432/chatflow` (admin / admin) |
| RabbitMQ Management | `http://<RabbitMQ-Public-IP>:15672` (admin / admin) |
| ALB (WebSocket) | `ws://chat-servers-1288018322.us-west-2.elb.amazonaws.com/chat/{roomId}` |

---

### Monitoring (EC2)

```bash
cd monitoring

# one-time snapshot
./check-metrics.sh

# live (requires: brew install watch)
./watch-metrics.sh
```

---

### Stop All Services

```bash
ssh -i <path-to-your-pem-key> ubuntu@<Server1-Public-IP> "./stop-server.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Server2-Public-IP> "./stop-server.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Server3-Public-IP> "./stop-server.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Server4-Public-IP> "./stop-server.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Consumer-Public-IP> "./stop-consumer.sh"
ssh -i <path-to-your-pem-key> ubuntu@<Postgres-Public-IP> "docker-compose down -v"
ssh -i <path-to-your-pem-key> ubuntu@<RabbitMQ-Public-IP> "docker-compose down -v"
ssh -i <path-to-your-pem-key> ubuntu@<Redis-Public-IP> "docker-compose down -v"
```

---

## Load Testing (JMeter)

JMeter test plans and results are in `jmeter-tests/`:

| Folder | Description |
|---|---|
| `jmeter-tests/original/` | Baseline and stress tests against the original (unoptimized) architecture |
| `jmeter-tests/optimized/` | Baseline and stress tests after database and caching optimizations |

Test parameters: 300 threads, 100 iterations/thread, targeting `/chat/{random room 1–20}` via ALB on port 80.

HTML dashboards are available in the respective `*_report-folder/` and `optimized_stress/` directories.

---

## Key Configuration

| Parameter | Default | Location |
|---|---|---|
| RabbitMQ connections per server | 2 | `server-v2/application.yaml` |
| RabbitMQ channels per connection | 25 | `server-v2/application.yaml` |
| Consumer thread count | 40 | `consumer-v3/application.yaml` |
| DB writer threads | 16 | `consumer-v3/application.yaml` |
| DB batch size | 1000 messages | `consumer-v3/application.yaml` |
| DB flush interval | 500 ms | `consumer-v3/application.yaml` |
| Analytics refresh interval | 60 s | `consumer-v3/application.yaml` |
| Circuit breaker failure threshold | 5 failures | `consumer-v3/application.yaml` |
| Circuit breaker cooldown | 30 s | `consumer-v3/application.yaml` |
| HikariCP max pool size | 20 | `consumer-v3/application.yaml` |
