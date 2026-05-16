# Analytics Before/After Measurement Guide

This guide explains how to run the project's local load test and compare
before/after results for analytics latency and overall throughput.

The load test driver in this repository is the `client` module. It opens
WebSocket connections to `server-v2`, sends a warmup phase plus a main load
phase, writes latency metrics to `client/results/`, and finally calls the
consumer analytics API.

## What You Are Measuring

Compare two scenarios:

1. Baseline: analytics queries served from the raw `messages` table
2. Optimized: analytics queries served from materialized views with scheduled
   refresh enabled

For both scenarios, record:

- analytics query latency from PostgreSQL `EXPLAIN ANALYZE`
- client-observed message latency and throughput
- consumer and database health during load

## Services Needed

For local measurement, run:

- Docker services from `deployment/docker-compose.yml`
  - PostgreSQL
  - RabbitMQ
  - Redis
- `server-v2`
- `consumer-v3`
- `client`

## Step 1: Start Local Dependencies

From the repo root:

```bash
cd deployment
docker-compose up -d
docker ps
```

Confirm these containers are running:

- `chatflow-db`
- `rabbitmq`
- `chatflow-redis`

## Step 2: Initialize the Database

From the repo root:

```bash
cd database
docker exec -i chatflow-db psql -U admin -d chatflow < schema.sql
docker exec -i chatflow-db psql -U admin -d chatflow < indexes.sql
docker exec -i chatflow-db psql -U admin -d chatflow < materialized_views.sql
```

If you want to start from a clean local database first:

```bash
cd deployment
docker-compose down -v
docker-compose up -d
cd ../database
docker exec -i chatflow-db psql -U admin -d chatflow < schema.sql
docker exec -i chatflow-db psql -U admin -d chatflow < indexes.sql
docker exec -i chatflow-db psql -U admin -d chatflow < materialized_views.sql
```

## Step 3: Start the Consumer

Open a dedicated terminal:

```bash
cd consumer-v3
GRADLE_USER_HOME=/tmp/gradle-group-chatflow ./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin --spring.data.redis.host=localhost --spring.data.redis.port=6379'
```

For faster local verification of automatic refresh, you can temporarily shorten
the refresh interval:

```bash
cd consumer-v3
GRADLE_USER_HOME=/tmp/gradle-group-chatflow ./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin --spring.data.redis.host=localhost --spring.data.redis.port=6379 --analytics.refresh-enabled=true --analytics.refresh-initial-delay-ms=1000 --analytics.refresh-interval-ms=5000'
```

Verify:

```bash
curl http://localhost:8081/health
```

## Step 4: Start the Server

Open another terminal:

```bash
cd server-v2
GRADLE_USER_HOME=/tmp/gradle-group-chatflow ./gradlew bootRun \
  --args='--spring.rabbitmq.host=localhost --spring.rabbitmq.port=5672 --spring.rabbitmq.username=admin --spring.rabbitmq.password=admin --spring.data.redis.host=localhost --spring.data.redis.port=6379 --server.url=http://localhost:8080 --consumer.url=http://localhost:8081'
```

Verify:

```bash
curl http://localhost:8080/health
```

## Step 5: Open Monitoring Terminals

Open one or two extra terminals before starting the load test.

### Consumer metrics

```bash
cd monitoring
./watch-metrics-local.sh
```

This watches `http://localhost:8081/metrics` every 2 seconds.

### Database metrics

```bash
cd monitoring
DB_NAME=chatflow DB_USER=admin DB_PASSWORD=admin DB_HOST=localhost DB_PORT=5432 ./watch-db-metrics-local.sh
```

This prints:

- active DB connections
- transaction count
- block reads and hits
- buffer hit ratio
- lock counts

## Step 6: Run Query Profiling

Before and after the optimization, run:

```bash
cd database
docker exec -i chatflow-db psql -U admin -d chatflow < explain_analytics.sql | tee explain_output.txt
```

To quickly inspect the key lines:

```bash
rg "Baseline|Materialized|Execution Time" explain_output.txt
```

Watch for:

- execution time
- whether the plan scans `messages` or `mv_*`
- whether small datasets distort the comparison

## Step 7: Run the Load Test

The load test entrypoint is the `client` module. The client supports runtime
configuration through environment variables.

Important variables:

- `SERVER_HOST`
- `SERVER_PORT`
- `METRICS_HOST`
- `METRICS_PORT`
- `TOTAL_MESSAGES`
- `MAIN_THREADS`

The client already includes:

- warmup phase: `32` threads x `1000` messages each
- main load phase: controlled by `TOTAL_MESSAGES` and `MAIN_THREADS`

### Baseline run

From the repo root:

```bash
cd client
SERVER_HOST=localhost \
SERVER_PORT=8080 \
METRICS_HOST=localhost \
METRICS_PORT=8081 \
TOTAL_MESSAGES=500000 \
MAIN_THREADS=128 \
GRADLE_USER_HOME=/tmp/gradle-group-chatflow \
./gradlew run
```

### Stress run

From the repo root:

```bash
cd client
SERVER_HOST=localhost \
SERVER_PORT=8080 \
METRICS_HOST=localhost \
METRICS_PORT=8081 \
TOTAL_MESSAGES=1000000 \
MAIN_THREADS=256 \
GRADLE_USER_HOME=/tmp/gradle-group-chatflow \
./gradlew run
```

These values match the intent of:

- `load-tests/configs/baseline.json`
- `load-tests/configs/stress.json`

## Step 8: Capture Client Results

After each run, the client writes:

- latency CSV:
  - `client/results/performance_metrics<threads>.csv`
- analytics API response:
  - `client/results/metrics_api_response.json`

The client console output also prints:

- successful vs failed messages
- overall throughput
- p50 / p95 / p99 latency
- room throughput
- throughput over time in 10-second buckets

## Step 9: Compare Before and After

Keep the dataset size and load settings the same across runs.

For each scenario, compare:

- `EXPLAIN ANALYZE` execution time
- client overall throughput
- client p50 / p95 / p99 latency
- consumer queue depth during load
- DB buffer hit ratio and active connections
- final analytics API response written to `client/results/metrics_api_response.json`

## Suggested Workflow

### Baseline

Use a code state where analytics queries still read from the raw `messages`
table, then run:

1. database initialization
2. consumer
3. server
4. monitoring scripts
5. `docker exec -i chatflow-db psql -U admin -d chatflow < explain_analytics.sql`
6. `cd client && ... ./gradlew run`

### Optimized

Use the feature branch with Tasks 1-4, then repeat the exact same sequence.

## Interpreting Results

- If `messages per minute`, `top users`, and `top rooms` execute faster from
  `mv_*`, the optimization helps query latency
- If client throughput is unchanged or improves while analytics latency drops,
  the feature is a net win
- If very frequent refresh causes queue depth or DB pressure to spike, increase
  `analytics.refresh-interval-ms`
- If small datasets show little gain, repeat with larger data volume before
  judging the optimization

## Related Files

- `database/explain_analytics.sql`
- `load-tests/configs/baseline.json`
- `load-tests/configs/stress.json`
- `monitoring/watch-metrics-local.sh`
- `monitoring/watch-db-metrics-local.sh`
- `client/results/`
