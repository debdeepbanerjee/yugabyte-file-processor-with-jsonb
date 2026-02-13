# Quick Start Guide

This guide will help you get the YugabyteDB File Processor running in under 5 minutes.

## Prerequisites

- Java 21 installed
- Docker installed (for easy YugabyteDB setup)

## Steps

### 1. Start YugabyteDB with Docker Compose

```bash
docker-compose up -d
```

This starts:
- YugabyteDB on port 5433
- PgAdmin on port 5050 (optional, for database management)

### 2. Initialize the Database

```bash
# Wait for YugabyteDB to be ready (about 30 seconds)
sleep 30

# Connect and run schema
docker exec -i yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte < src/main/resources/schema.sql

# Generate test data
docker exec -i yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte < src/main/resources/test-data.sql
```

### 3. Build and Run the Application

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun
```

The application will:
1. Start polling for work
2. Pick up master records based on priority
3. Stream detail records from the database
4. Generate output files in `./output` directory

### 4. Monitor Progress

Watch the logs:
```bash
tail -f logs/application.log
```

Or check the database:
```sql
docker exec -it yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte

-- Check processing status
SELECT * FROM processing_status;

-- Check completed masters
SELECT master_id, business_center_code, status, locked_by
FROM master_records
WHERE status = 'COMPLETED'
ORDER BY updated_at DESC
LIMIT 10;
```

### 5. View Generated Files

```bash
ls -lh output/
cat output/NYC_1_*.txt
```

## Running Multiple Instances

To test horizontal scaling:

```bash
# Terminal 1
./gradlew bootRun

# Terminal 2
./gradlew bootRun --args='--server.port=8081'

# Terminal 3
./gradlew bootRun --args='--server.port=8082'
```

Each instance will process different master_ids concurrently!

## Cleanup

```bash
# Stop YugabyteDB
docker-compose down

# Remove volumes (optional - deletes all data)
docker-compose down -v

# Clean build artifacts
./gradlew clean
rm -rf output/
```

## Next Steps

- Customize business center priorities in `src/main/resources/application.yml`
- Adjust concurrency settings for your workload
- Review the main README.md for detailed configuration options
- Check the monitoring endpoints at `http://localhost:8080/actuator`

## Troubleshooting

**YugabyteDB won't start:**
```bash
# Check if port 5433 is already in use
lsof -i :5433

# View YugabyteDB logs
docker logs yugabyte-db
```

**No records being processed:**
```bash
# Verify test data was loaded
docker exec -it yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte \
  -c "SELECT COUNT(*) FROM master_records WHERE status = 'PENDING';"
```

**Application won't start:**
```bash
# Verify Java version
java -version  # Should be 21 or higher

# Check database connectivity
docker exec -it yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte -c "SELECT 1;"
```
