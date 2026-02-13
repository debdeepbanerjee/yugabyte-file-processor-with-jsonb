# JSONB Quick Reference Guide

## What is JSONB?

JSONB is PostgreSQL's (and YugabyteDB's) binary JSON data type that allows storing, indexing, and querying semi-structured data efficiently.

## Quick Start

### 1. Add Jackson Dependencies (already included)
```kotlin
// build.gradle.kts
implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
```

### 2. Create Java Model with Jackson Annotations
```java
@Data
public class TransactionData {
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @JsonProperty("customer")
    private Customer customer;
    
    @JsonProperty("items")
    private List<LineItem> items;
}
```

### 3. Read JSONB from Database
```java
public EnhancedDetailRecord mapRow(ResultSet rs, int rowNum) {
    // Read JSONB column as PGobject
    Object jsonbObject = rs.getObject("transaction_data");
    
    if (jsonbObject instanceof PGobject pgObject) {
        String jsonString = pgObject.getValue();
        
        // Unmarshal using Jackson ObjectMapper
        TransactionData txnData = objectMapper.readValue(
            jsonString, 
            TransactionData.class
        );
    }
}
```

### 4. Stream Records (Memory Efficient)
```java
Stream<EnhancedDetailRecord> stream = 
    jdbcTemplate.queryForStream(sql, rowMapper, masterId);

stream.forEach(record -> {
    // Process each record with unmarshalled JSONB
    TransactionData txn = record.getTransactionData();
    String customerId = txn.getCustomer().getCustomerId();
});
```

### 5. Write to File with BeanIO
```java
// Flatten nested JSON structure
EnhancedDetailOutput output = EnhancedDetailOutput.builder()
    .customerId(txn.getCustomer().getCustomerId())
    .merchantName(txn.getMerchant().getName())
    .itemCount(txn.getItems().size())
    .build();

beanWriter.write("detail", output);
```

## Common SQL Operations

### Insert JSONB
```sql
INSERT INTO enhanced_detail_records (transaction_data)
VALUES ('{
    "customer_id": "CUST001",
    "amount": 100.50,
    "items": [{"name": "Item1"}]
}'::jsonb);
```

### Query by JSONB Field (uses expression index)
```sql
-- Single field
SELECT * FROM enhanced_detail_records
WHERE transaction_data->>'customer_id' = 'CUST001';

-- Nested field
SELECT * FROM enhanced_detail_records
WHERE transaction_data->'customer'->>'email' = 'john@example.com';
```

### Containment Query (uses GIN index)
```sql
-- Check if JSONB contains specific structure
SELECT * FROM enhanced_detail_records
WHERE transaction_data @> '{"status": "COMPLETED"}'::jsonb;
```

### Update JSONB Field
```sql
-- Update single field without replacing entire JSONB
UPDATE enhanced_detail_records
SET transaction_data = jsonb_set(
    transaction_data,
    '{status}',
    '"APPROVED"'
)
WHERE detail_id = 123;
```

### Extract Fields
```sql
SELECT 
    transaction_data->>'transaction_id' as txn_id,
    transaction_data->'customer'->>'name' as customer,
    jsonb_array_length(transaction_data->'items') as item_count
FROM enhanced_detail_records;
```

## Indexing Strategies

### 1. GIN Index (for containment queries)
```sql
CREATE INDEX idx_jsonb_gin 
ON enhanced_detail_records USING GIN (transaction_data);
```
**Use when:** Querying with `@>`, `@?`, `?` operators

### 2. Expression Index (for specific paths)
```sql
CREATE INDEX idx_customer_id 
ON enhanced_detail_records ((transaction_data->>'customer_id'));
```
**Use when:** Frequently querying specific JSON paths

### 3. Functional Index (for computed values)
```sql
CREATE INDEX idx_risk_score 
ON enhanced_detail_records (((transaction_data->>'risk_score')::NUMERIC));
```
**Use when:** Querying computed or type-cast values

## Performance Tips

### ✅ DO:
- Use expression indexes for frequently queried paths
- Stream large result sets with `queryForStream()`
- Use `@>` containment operator for complex queries
- Type-cast extracted values: `(jsonb_field->>'amount')::NUMERIC`

### ❌ DON'T:
- Load all JSONB records into memory at once
- Query without indexes on high-cardinality fields
- Store huge arrays (>1000 elements) in single JSONB
- Use `LIKE` on JSONB - use GIN index operators instead

## Common Patterns

### Pattern 1: Extract and Aggregate
```sql
SELECT 
    transaction_data->'merchant'->>'category' as category,
    COUNT(*) as count,
    AVG((transaction_data->>'amount')::NUMERIC) as avg_amount
FROM enhanced_detail_records
GROUP BY category;
```

### Pattern 2: Filter by Multiple JSONB Fields
```sql
SELECT * FROM enhanced_detail_records
WHERE transaction_data @> '{"status": "COMPLETED"}'::jsonb
  AND (transaction_data->>'risk_score')::NUMERIC < 50;
```

### Pattern 3: Update Nested Field
```sql
UPDATE enhanced_detail_records
SET transaction_data = jsonb_set(
    transaction_data,
    '{customer,loyalty_tier}',
    '"PLATINUM"'
)
WHERE transaction_data->'customer'->>'customer_id' = 'CUST001';
```

### Pattern 4: Add Field to Existing JSONB
```sql
UPDATE enhanced_detail_records
SET transaction_data = transaction_data || 
    '{"reviewed": true, "review_date": "2025-02-13"}'::jsonb
WHERE detail_id = 123;
```

## Java Patterns

### Pattern 1: Safe Null Handling
```java
TransactionData txn = record.getTransactionData();
String email = Optional.ofNullable(txn)
    .map(TransactionData::getCustomer)
    .map(Customer::getEmail)
    .orElse("unknown");
```

### Pattern 2: Stream and Flatten
```java
enhancedRecords.stream()
    .filter(r -> r.getTransactionData() != null)
    .map(this::flattenRecord)
    .forEach(beanWriter::write);
```

### Pattern 3: Collect Statistics
```java
DoubleSummaryStatistics riskStats = enhancedRecords.stream()
    .map(EnhancedDetailRecord::getTransactionData)
    .filter(Objects::nonNull)
    .map(TransactionData::getRiskScore)
    .filter(Objects::nonNull)
    .collect(Collectors.summarizingDouble(Double::doubleValue));
```

## Troubleshooting

### Issue: Jackson can't deserialize JSONB
**Solution:** Ensure @JsonProperty names match JSON keys exactly:
```java
@JsonProperty("customer_id")  // Must match "customer_id" in JSON
private String customerId;
```

### Issue: NullPointerException on nested fields
**Solution:** Use null-safe extraction:
```java
String city = Optional.ofNullable(txn.getCustomer())
    .map(Customer::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

### Issue: JSONB queries are slow
**Solution:** Add appropriate indexes:
```sql
-- For equality checks
CREATE INDEX ON table ((jsonb_col->>'field_name'));

-- For containment
CREATE INDEX ON table USING GIN (jsonb_col);
```

## File Output Example

Input JSONB:
```json
{
  "transaction_id": "TXN123",
  "customer": {
    "customer_id": "CUST001",
    "email": "john@example.com"
  },
  "merchant": {
    "name": "Coffee Shop"
  },
  "items": [
    {"name": "Coffee", "price": 5.00},
    {"name": "Muffin", "price": 3.50}
  ]
}
```

Flattened Output:
```
DETAIL|123|ACC001|...|TXN123|CUST001|john@example.com|Coffee Shop|2|...
```

## Resources

- **PostgreSQL JSONB Docs**: https://www.postgresql.org/docs/current/datatype-json.html
- **YugabyteDB JSONB**: https://docs.yugabyte.com/preview/api/ysql/datatypes/type_json/
- **Jackson Databind**: https://github.com/FasterXML/jackson-databind

## Summary

JSONB in YugabyteDB + Jackson in Java = Flexible, performant semi-structured data processing with full ACID guarantees and the ability to scale horizontally.
