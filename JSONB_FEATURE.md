# JSONB Feature Documentation

## Overview

This application now includes support for reading complex JSON data stored as JSONB in YugabyteDB, unmarshalling it into Java objects using Jackson, and writing the flattened data to files using BeanIO.

## Architecture

```
┌──────────────────────────────────────┐
│  YugabyteDB                          │
│  enhanced_detail_records table       │
│  ┌────────────────────────────────┐  │
│  │ detail_id        BIGINT        │  │
│  │ master_id        BIGINT        │  │
│  │ account_number   VARCHAR       │  │
│  │ amount           DECIMAL       │  │
│  │ transaction_data JSONB  ◄──────┼──┼─── Binary JSON storage
│  │   {                            │  │
│  │     customer: {...},           │  │
│  │     merchant: {...},           │  │
│  │     payment_method: {...},     │  │
│  │     items: [...]               │  │
│  │   }                            │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
               │
               ↓ JDBC Query
┌──────────────────────────────────────┐
│  EnhancedDetailRecordRepository      │
│  - Reads JSONB as PGobject           │
│  - Unmarshals to TransactionData     │
└──────────────────────────────────────┘
               │
               ↓ Jackson ObjectMapper
┌──────────────────────────────────────┐
│  TransactionData (Java Object)       │
│  - customer: Customer                │
│  - merchant: Merchant                │
│  - paymentMethod: PaymentMethod      │
│  - items: List<LineItem>             │
└──────────────────────────────────────┘
               │
               ↓ Flatten nested structure
┌──────────────────────────────────────┐
│  EnhancedDetailOutput                │
│  - customerId                        │
│  - customerEmail                     │
│  - merchantName                      │
│  - paymentType                       │
│  - itemCount                         │
└──────────────────────────────────────┘
               │
               ↓ BeanIO Writer
┌──────────────────────────────────────┐
│  Output File (Pipe-delimited)        │
│  DETAIL|123|ACC001|...|CUST001|...   │
└──────────────────────────────────────┘
```

## Key Components

### 1. Database Schema

**Table: `enhanced_detail_records`**
- Standard columns: `detail_id`, `master_id`, `amount`, etc.
- **JSONB column**: `transaction_data` - stores complex JSON
- **Indexes**:
  - GIN index on entire JSONB column for containment queries
  - Expression indexes on frequently queried JSON paths
  - Hash partitioning for horizontal scaling

```sql
CREATE TABLE enhanced_detail_records (
    detail_id BIGSERIAL,
    master_id BIGINT,
    -- ... other columns ...
    transaction_data JSONB,  -- Complex JSON structure
    PRIMARY KEY (master_id, detail_id)
) PARTITION BY HASH (master_id);

-- GIN index for fast JSONB queries
CREATE INDEX idx_enhanced_detail_jsonb 
    ON enhanced_detail_records USING GIN (transaction_data);

-- Expression index for customer_id
CREATE INDEX idx_enhanced_detail_customer_id 
    ON enhanced_detail_records ((transaction_data->>'customer_id'));
```

### 2. Java Models

**TransactionData.java** - Complex nested structure matching JSONB:
```java
@Data
public class TransactionData {
    private String transactionId;
    private BigDecimal amount;
    private Customer customer;          // Nested object
    private Merchant merchant;          // Nested object
    private PaymentMethod paymentMethod; // Nested object
    private List<LineItem> items;       // Nested array
    private Map<String, Object> metadata; // Dynamic fields
}
```

**EnhancedDetailRecord.java** - Combines relational and JSONB:
```java
@Data
public class EnhancedDetailRecord {
    // Relational columns
    private Long detailId;
    private String accountNumber;
    private BigDecimal amount;
    
    // JSONB column unmarshalled to object
    private TransactionData transactionData;
}
```

### 3. Repository Layer

**EnhancedDetailRecordRepository.java** - JSONB unmarshalling:
```java
private class EnhancedDetailRecordRowMapper implements RowMapper<EnhancedDetailRecord> {
    public EnhancedDetailRecord mapRow(ResultSet rs, int rowNum) {
        // Read JSONB as PGobject
        Object jsonbObject = rs.getObject("transaction_data");
        
        if (jsonbObject instanceof PGobject pgObject) {
            String jsonString = pgObject.getValue();
            
            // Unmarshal to Java object using Jackson
            TransactionData txnData = objectMapper.readValue(
                jsonString, 
                TransactionData.class
            );
            record.setTransactionData(txnData);
        }
        
        return record;
    }
}
```

### 4. File Writing

**EnhancedFileWriterService.java** - Flattens nested JSON:
```java
private EnhancedDetailOutput flattenRecord(EnhancedDetailRecord record) {
    TransactionData txnData = record.getTransactionData();
    
    return EnhancedDetailOutput.builder()
        .customerId(txnData.getCustomer().getCustomerId())
        .customerEmail(txnData.getCustomer().getEmail())
        .merchantName(txnData.getMerchant().getName())
        .paymentType(txnData.getPaymentMethod().getType())
        .itemCount(txnData.getItems().size())
        .build();
}
```

## Usage

### 1. Setup Database

```bash
# Create schema
docker exec -i yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte < src/main/resources/schema.sql

# Generate test data with JSONB
docker exec -i yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte < src/main/resources/test-data-enhanced.sql
```

### 2. Process Enhanced Records

The application automatically processes enhanced records when they're available:

```java
@Service
public class EnhancedRecordProcessingService {
    public boolean processNextEnhancedMaster() {
        // 1. Find and lock master_id
        // 2. Stream enhanced_detail_records with JSONB
        // 3. Unmarshal JSONB to TransactionData objects
        // 4. Flatten and write to file
        // 5. Update status
    }
}
```

### 3. Output File Format

Enhanced files include flattened JSONB data:

```
HEADER|12345|NYC|20250213|100|2.0
DETAIL|1|ACC001|John Doe|1500.50|USD|...|TXN001|PURCHASE|CUST001|john@example.com|555-0001|New York|NY|USA|MERCH001|Coffee Shop|Restaurant|CREDIT_CARD|4532|VISA|85.5|COMPLETED|3
DETAIL|2|ACC002|Jane Smith|2300.75|EUR|...|TXN002|REFUND|CUST002|jane@example.com|555-0002|London|England|UK|MERCH002|Retail Store|Retail|DEBIT_CARD|8765|MASTERCARD|12.3|PENDING|5
...
TRAILER|100|123456.78|45.67|87
```

Fields extracted from JSONB:
- `transactionId` - from `transaction_data.transaction_id`
- `customerId` - from `transaction_data.customer.customer_id`
- `customerEmail` - from `transaction_data.customer.email`
- `merchantName` - from `transaction_data.merchant.name`
- `paymentType` - from `transaction_data.payment_method.type`
- `riskScore` - from `transaction_data.risk_score`
- `itemCount` - count of `transaction_data.items[]`

## JSONB Query Examples

### Query by Customer ID (uses expression index)
```sql
SELECT * FROM enhanced_detail_records
WHERE transaction_data->>'customer_id' = 'CUST000123';
```

### Query by Status (uses expression index)
```sql
SELECT * FROM enhanced_detail_records
WHERE transaction_data->>'status' = 'COMPLETED';
```

### Query by Risk Score Range (uses expression index)
```sql
SELECT * FROM enhanced_detail_records
WHERE (transaction_data->>'risk_score')::NUMERIC > 80;
```

### Containment Query (uses GIN index)
```sql
SELECT * FROM enhanced_detail_records
WHERE transaction_data @> '{"status": "COMPLETED", "currency": "USD"}'::JSONB;
```

### Extract Nested Fields
```sql
SELECT 
    detail_id,
    transaction_data->>'transaction_id' as txn_id,
    transaction_data->'customer'->>'name' as customer_name,
    transaction_data->'customer'->'address'->>'city' as city,
    transaction_data->'merchant'->>'name' as merchant,
    jsonb_array_length(transaction_data->'items') as item_count
FROM enhanced_detail_records;
```

## Performance Considerations

### Indexing Strategy

1. **GIN Index**: Fast for containment queries (`@>`, `@?`, `?`)
```sql
CREATE INDEX ON enhanced_detail_records USING GIN (transaction_data);
```

2. **Expression Indexes**: Fast for specific path queries
```sql
CREATE INDEX ON enhanced_detail_records ((transaction_data->>'customer_id'));
CREATE INDEX ON enhanced_detail_records ((transaction_data->>'status'));
```

### Memory Efficiency

- **Streaming**: Records are processed one at a time, never loading all JSONB into memory
- **Jackson**: Unmarshals only when needed, garbage collected immediately
- **Virtual Threads**: Non-blocking I/O prevents thread starvation

### Scaling

- **Partitioning**: Hash partitioning distributes JSONB data across nodes
- **Compression**: JSONB is automatically compressed in YugabyteDB
- **Horizontal Scaling**: Multiple instances can process different master_ids

## Integration with Existing System

The JSONB feature integrates seamlessly:

1. **Same Locking Mechanism**: Uses master_records table for coordination
2. **Same Priority System**: Business center priorities apply
3. **Same Output Directory**: Enhanced files written to configured location
4. **Same Monitoring**: Uses existing actuator endpoints

## Testing

### Generate Test Data
```bash
# Create 20 masters with ~100-500 enhanced records each
docker exec -i yugabyte-db bin/ysqlsh -h localhost -U yugabyte -d yugabyte < src/main/resources/test-data-enhanced.sql
```

### Verify JSONB Data
```sql
-- Check sample records
SELECT 
    detail_id,
    jsonb_pretty(transaction_data) 
FROM enhanced_detail_records 
LIMIT 1;

-- Verify indexes are being used
EXPLAIN ANALYZE
SELECT * FROM enhanced_detail_records
WHERE transaction_data->>'customer_id' = 'CUST000001';
```

### Run Enhanced Processing
```bash
./gradlew bootRun
```

Watch for logs:
```
Processing enhanced master_id: 123 with 250 detail records containing JSONB
Unmarshalled JSONB for detail_id: 456, transaction_id: TXN789
Written 250 enhanced records for master_id: 123
```

## Advanced Features

### 1. Custom JSON Fields

Add custom fields to `TransactionData`:
```java
@JsonProperty("custom_field")
private String customField;
```

### 2. Dynamic Metadata

The `metadata` map accepts any JSON:
```java
Map<String, Object> metadata = txnData.getMetadata();
String ipAddress = (String) metadata.get("ip_address");
```

### 3. JSONB Updates

Update specific paths without replacing entire JSONB:
```sql
UPDATE enhanced_detail_records
SET transaction_data = jsonb_set(
    transaction_data,
    '{status}',
    '"COMPLETED"'
)
WHERE detail_id = 123;
```

### 4. Aggregations on JSONB

```sql
SELECT 
    transaction_data->'merchant'->>'category' as category,
    COUNT(*) as transaction_count,
    AVG((transaction_data->>'risk_score')::NUMERIC) as avg_risk
FROM enhanced_detail_records
GROUP BY category;
```

## Troubleshooting

### Issue: JSONB not unmarshalling

**Check:**
1. Verify Jackson dependency in `build.gradle.kts`
2. Check ObjectMapper is configured with JavaTimeModule
3. Ensure @JsonProperty names match database JSON keys

### Issue: Index not being used

**Solution:**
```sql
-- Verify index exists
\d+ enhanced_detail_records

-- Force index usage
SET enable_seqscan = off;

-- Analyze table statistics
ANALYZE enhanced_detail_records;
```

### Issue: Out of memory with JSONB

**Solution:**
- Reduce batch size in `application.yml`
- Verify streaming is working (check logs)
- Increase JVM heap if needed

## Benefits of JSONB Approach

1. **Flexibility**: Schema-less JSON allows easy field additions
2. **Performance**: Binary format is faster than text JSON
3. **Indexing**: GIN and expression indexes enable fast queries
4. **Validation**: JSON structure validated at application level
5. **Compatibility**: Works with existing PostgreSQL ecosystem

## File Structure

```
src/main/java/com/example/
├── model/
│   ├── TransactionData.java          # Complex JSON model
│   └── EnhancedDetailRecord.java     # Record with JSONB
├── repository/
│   └── EnhancedDetailRecordRepository.java  # JSONB unmarshalling
├── service/
│   ├── EnhancedFileWriterService.java      # Flatten & write
│   └── EnhancedRecordProcessingService.java # Orchestration
├── beanio/
│   ├── EnhancedDetailOutput.java     # Flattened output
│   └── EnhancedFileModels.java       # Headers/trailers
└── config/
    └── JacksonConfig.java            # ObjectMapper setup

src/main/resources/
├── beanio-mapping-enhanced.xml       # BeanIO config
├── schema.sql                        # Table with JSONB
└── test-data-enhanced.sql            # JSONB test data
```

## Next Steps

1. **Custom JSON Schemas**: Define JSON schemas for validation
2. **JSONB Triggers**: Add database triggers for JSONB changes
3. **Real-time Processing**: Process JSONB changes as they happen
4. **Analytics**: Build dashboards querying JSONB fields
5. **Machine Learning**: Use JSONB fields for ML feature extraction
