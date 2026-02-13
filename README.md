# YugabyteDB File Processor

A highly scalable Spring Boot application that processes records from YugabyteDB and generates files using BeanIO, leveraging Java 21 virtual threads for non-blocking I/O operations.

## Author

- **Name**: Debdeep Banerjee

## Features

- ✅ **Java 21 Virtual Threads**: All I/O operations use virtual threads for maximum scalability
- ✅ **YugabyteDB Integration**: Uses plain JDBC with YugabyteDB (PostgreSQL-compatible)
- ✅ **Priority-Based Processing**: Business center codes drive processing priority
- ✅ **Distributed Locking**: Pessimistic locking with `FOR UPDATE SKIP LOCKED` prevents duplicate processing
- ✅ **BeanIO File Generation**: Efficient file writing with header/detail/trailer format
- ✅ **Horizontal Scalability**: Multiple instances can run concurrently without conflicts
- ✅ **Memory Efficient**: Streams millions of records without loading all into memory
- ✅ **Gradle with Kotlin DSL**: Modern build configuration

## Architecture

```
┌─────────────────┐
│   Instance 1    │──┐
└─────────────────┘  │
                     │    ┌──────────────────┐
┌─────────────────┐  ├───→│  YugabyteDB      │
│   Instance 2    │──┤    │  - master_records│
└─────────────────┘  │    │  - detail_records│
                     │    └──────────────────┘
┌─────────────────┐  │
│   Instance N    │──┘
└─────────────────┘
        │
        ↓
   File Output
```

### Processing Flow

1. **Polling**: Each instance continuously polls for available `master_id` records
2. **Priority Selection**: Records are selected based on business center priority (configured in `application.yml`)
3. **Locking**: Selected `master_id` is locked using database-level pessimistic locking
4. **Streaming**: Detail records are streamed from the database (memory-efficient)
5. **File Writing**: BeanIO writes records to a delimited file with header and trailer
6. **Completion**: Lock is released and status updated to `COMPLETED`

## Technology Stack

- **Java**: 21 (Virtual Threads)
- **Spring Boot**: 3.2.2
- **Database**: YugabyteDB (PostgreSQL-compatible)
- **File Processing**: BeanIO 2.1.0
- **Build Tool**: Gradle 8.x with Kotlin DSL
- **JDBC**: Direct JDBC with JdbcTemplate (non-blocking via virtual threads)

## Prerequisites

- Java 21 or higher
- YugabyteDB running (or PostgreSQL 11+)
- Gradle 8.x (or use the wrapper)

## Setup

### 1. Start YugabyteDB

```bash
# Using Docker
docker run -d --name yugabyte \
  -p 5433:5433 \
  yugabytedb/yugabyte:latest \
  bin/yugabyted start --daemon=false

# Or download and run locally
# https://docs.yugabyte.com/preview/quick-start/
```

### 2. Create Database Schema

```bash
# Connect to YugabyteDB
psql -h localhost -p 5433 -U yugabyte -d yugabyte

# Run the schema script
\i src/main/resources/schema.sql
```

### 3. Configure Application

Edit `src/main/resources/application.yml` to match your environment:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/yugabyte
    username: yugabyte
    password: yugabyte

processor:
  business-center-priorities:
    NYC: 100  # Highest priority
    LON: 90
    TKY: 80
    # Add more as needed
  
  max-concurrent-masters: 10
  batch-size: 1000
  output-directory: ./output
```

### 4. Build the Application

```bash
./gradlew clean build
```

### 5. Run the Application

```bash
./gradlew bootRun
```

Or run the JAR:

```bash
java -jar build/libs/yugabyte-file-processor-1.0.0.jar
```

## Configuration Reference

### Database Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `spring.datasource.url` | JDBC URL for YugabyteDB | `jdbc:postgresql://localhost:5433/yugabyte` |
| `spring.datasource.username` | Database username | `yugabyte` |
| `spring.datasource.password` | Database password | `yugabyte` |
| `spring.datasource.hikari.maximum-pool-size` | Max connection pool size | `20` |

### Processing Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `processor.business-center-priorities` | Map of business center codes to priorities | See `application.yml` |
| `processor.batch-size` | JDBC fetch size for streaming | `1000` |
| `processor.lock-timeout-seconds` | Seconds before a stale lock expires | `300` |
| `processor.poll-interval-seconds` | Seconds between polls when no work available | `5` |
| `processor.max-concurrent-masters` | Max concurrent `master_id` processing | `10` |
| `processor.output-directory` | Directory for generated files | `./output` |

## Database Schema

### `master_records`

Stores master batch information and locking state.

| Column | Type | Description |
|--------|------|-------------|
| `master_id` | BIGSERIAL | Primary key |
| `business_center_code` | VARCHAR(10) | Business center identifier |
| `priority` | INT | Processing priority (higher = more urgent) |
| `status` | VARCHAR(20) | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `locked_by` | VARCHAR(255) | Instance ID that locked this record |
| `locked_at` | TIMESTAMP | When the lock was acquired |
| `error_message` | TEXT | Error details if status is `FAILED` |

### `detail_records`

Stores detail records associated with each master. Hash-partitioned for performance.

| Column | Type | Description |
|--------|------|-------------|
| `detail_id` | BIGSERIAL | Detail record identifier |
| `master_id` | BIGINT | Foreign key to master_records |
| `record_type` | VARCHAR(20) | Type of transaction |
| `account_number` | VARCHAR(50) | Account identifier |
| `customer_name` | VARCHAR(200) | Customer name |
| `amount` | DECIMAL(18,2) | Transaction amount |
| `currency` | VARCHAR(3) | Currency code |
| `description` | TEXT | Transaction description |
| `transaction_date` | TIMESTAMP | Transaction timestamp |

## Locking Mechanism

The application uses PostgreSQL's `FOR UPDATE SKIP LOCKED` to implement distributed locking:

```sql
SELECT master_id
FROM master_records
WHERE status = 'PENDING'
  AND (locked_by IS NULL OR locked_at < NOW() - INTERVAL '300 seconds')
ORDER BY priority DESC, created_at ASC
LIMIT 1
FOR UPDATE SKIP LOCKED
```

**Benefits:**
- No contention between instances
- Automatic skip of locked rows
- Stale lock recovery via timeout
- Database-enforced consistency

## File Format

Generated files use pipe-delimited format with BeanIO:

```
HEADER|{master_id}|{business_center_code}|{date}|{record_count}
{record_type}|{detail_id}|{account_number}|{customer_name}|{amount}|{currency}|{description}|{transaction_date}
...
TRAILER|{total_records}|{total_amount}
```

Example:
```
HEADER|12345|NYC|20250209|3
TXN|1|ACC0001234567|John Doe|1500.50|USD|Payment received|20250209143022
TXN|2|ACC0007654321|Jane Smith|2300.75|USD|Transfer out|20250209143155
TXN|3|ACC0009876543|Bob Johnson|500.00|USD|Deposit|20250209144301
TRAILER|3|4301.25
```

## Horizontal Scaling

To run multiple instances:

```bash
# Instance 1
java -jar build/libs/yugabyte-file-processor-1.0.0.jar --server.port=8080

# Instance 2
java -jar build/libs/yugabyte-file-processor-1.0.0.jar --server.port=8081

# Instance 3
java -jar build/libs/yugabyte-file-processor-1.0.0.jar --server.port=8082
```

Each instance will:
- Generate a unique instance ID
- Independently poll for work
- Process different `master_id` records concurrently
- Never conflict due to database locking

## Monitoring

### Actuator Endpoints

Spring Boot Actuator is enabled for monitoring:

- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Info: `http://localhost:8080/actuator/info`

### Database Monitoring

Use the provided view:

```sql
SELECT * FROM processing_status;
```

Check for stale locks:

```sql
SELECT master_id, locked_by, locked_at, 
       EXTRACT(EPOCH FROM (NOW() - locked_at)) as lock_age_seconds
FROM master_records
WHERE locked_by IS NOT NULL;
```

Clean up stale locks:

```sql
SELECT cleanup_stale_locks(300);
```

## Performance Tuning

### For Millions of Records

1. **Adjust Batch Size**: Increase `processor.batch-size` for larger fetches
   ```yaml
   processor:
     batch-size: 5000
   ```

2. **Increase Concurrent Processing**: More virtual threads can handle more masters
   ```yaml
   processor:
     max-concurrent-masters: 50
   ```

3. **Tune Connection Pool**: Match pool size to concurrency
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 50
   ```

4. **Partition Detail Records**: YugabyteDB automatically distributes data across nodes

5. **Enable Compression**: For large files, compress output
   ```java
   // Add GZIP compression in FileWriterService
   GZIPOutputStream gzipStream = new GZIPOutputStream(
       Files.newOutputStream(outputPath)
   );
   ```

## Testing

### Insert Test Data

```sql
-- Generate master records
INSERT INTO master_records (business_center_code, priority, status)
SELECT 
    CASE (random() * 5)::INT
        WHEN 0 THEN 'NYC'
        WHEN 1 THEN 'LON'
        WHEN 2 THEN 'TKY'
        WHEN 3 THEN 'HKG'
        ELSE 'SIN'
    END,
    (random() * 100)::INT,
    'PENDING'
FROM generate_series(1, 100);

-- Generate detail records (1000 per master)
INSERT INTO detail_records (
    master_id, record_type, account_number, customer_name, 
    amount, currency, description, transaction_date
)
SELECT 
    m.master_id,
    'TXN',
    'ACC' || LPAD((random() * 10000000)::INT::TEXT, 10, '0'),
    'Customer ' || (random() * 1000)::INT,
    (random() * 10000)::DECIMAL(18,2),
    'USD',
    'Transaction ' || gs,
    CURRENT_TIMESTAMP - (random() * INTERVAL '365 days')
FROM master_records m
CROSS JOIN generate_series(1, 1000) gs
WHERE m.status = 'PENDING';
```

## Troubleshooting

### Issue: No records being processed

**Check:**
1. Ensure records exist with `status = 'PENDING'`
2. Verify database connection in logs
3. Check priority configuration matches business center codes

### Issue: Stale locks preventing processing

**Solution:**
```sql
-- Manually clean stale locks
UPDATE master_records
SET locked_by = NULL, locked_at = NULL, status = 'PENDING'
WHERE locked_at < NOW() - INTERVAL '5 minutes'
  AND status = 'PROCESSING';
```

### Issue: Out of memory with large files

**Solution:**
- Verify streaming is working (check logs for "Written N records")
- Increase JVM heap: `java -Xmx4g -jar ...`
- Reduce batch size if needed

## Project Structure

```
yugabyte-file-processor/
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── YugabyteFileProcessorApplication.java
│   │   │   ├── config/
│   │   │   │   ├── ProcessorConfigProperties.java
│   │   │   │   └── VirtualThreadConfig.java
│   │   │   ├── model/
│   │   │   │   ├── MasterRecord.java
│   │   │   │   └── DetailRecord.java
│   │   │   ├── beanio/
│   │   │   │   ├── FileHeader.java
│   │   │   │   └── FileTrailer.java
│   │   │   ├── repository/
│   │   │   │   ├── MasterRecordRepository.java
│   │   │   │   └── DetailRecordRepository.java
│   │   │   ├── service/
│   │   │   │   ├── FileWriterService.java
│   │   │   │   └── RecordProcessingService.java
│   │   │   └── scheduler/
│   │   │       └── ProcessingScheduler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── beanio-mapping.xml
│   │       └── schema.sql
│   └── test/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## License

This project is licensed under the MIT License.

## Contributing

Contributions are welcome! Please submit pull requests or open issues for any improvements.
