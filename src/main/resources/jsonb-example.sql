-- JSONB End-to-End Example
-- This script demonstrates the complete flow of JSONB data from database to file

-- ============================================================================
-- STEP 1: Insert a master record
-- ============================================================================

INSERT INTO master_records (business_center_code, priority, status)
VALUES ('NYC', 100, 'PENDING')
RETURNING master_id;

-- Let's say this returns master_id = 999

-- ============================================================================
-- STEP 2: Insert enhanced detail records with complex JSONB data
-- ============================================================================

-- Example 1: E-commerce purchase
INSERT INTO enhanced_detail_records (
    master_id,
    record_type,
    account_number,
    customer_name,
    amount,
    currency,
    description,
    transaction_date,
    transaction_data,
    processing_status
) VALUES (
    999,
    'PURCHASE',
    'ACC0012345678',
    'John Smith',
    1299.99,
    'USD',
    'Online purchase - Electronics',
    '2025-02-13 14:30:00',
    '{
        "transaction_id": "TXN000123456789",
        "transaction_type": "PURCHASE",
        "amount": 1299.99,
        "currency": "USD",
        "timestamp": "2025-02-13T14:30:00",
        "customer": {
            "customer_id": "CUST001234",
            "name": "John Smith",
            "email": "john.smith@example.com",
            "phone": "+1-555-0123",
            "address": {
                "street": "123 Main Street",
                "city": "New York",
                "state": "NY",
                "postal_code": "10001",
                "country": "USA"
            },
            "loyalty_tier": "GOLD"
        },
        "merchant": {
            "merchant_id": "MERCH5678",
            "name": "TechStore Online",
            "category": "Electronics",
            "mcc": "5732"
        },
        "payment_method": {
            "type": "CREDIT_CARD",
            "last_four": "4532",
            "brand": "VISA",
            "expiry_month": 12,
            "expiry_year": 2027
        },
        "items": [
            {
                "item_id": "ITEM001",
                "name": "Laptop Computer",
                "quantity": 1,
                "unit_price": 999.99,
                "total_price": 999.99,
                "category": "Computers"
            },
            {
                "item_id": "ITEM002",
                "name": "Wireless Mouse",
                "quantity": 2,
                "unit_price": 49.99,
                "total_price": 99.98,
                "category": "Accessories"
            },
            {
                "item_id": "ITEM003",
                "name": "USB-C Cable",
                "quantity": 1,
                "unit_price": 19.99,
                "total_price": 19.99,
                "category": "Accessories"
            }
        ],
        "metadata": {
            "ip_address": "192.168.1.100",
            "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "session_id": "sess_abc123xyz",
            "referrer": "https://google.com",
            "device_type": "desktop"
        },
        "risk_score": 15.5,
        "status": "COMPLETED"
    }'::jsonb,
    'PENDING'
);

-- Example 2: Restaurant payment
INSERT INTO enhanced_detail_records (
    master_id,
    record_type,
    account_number,
    customer_name,
    amount,
    currency,
    description,
    transaction_date,
    transaction_data,
    processing_status
) VALUES (
    999,
    'PAYMENT',
    'ACC0087654321',
    'Sarah Johnson',
    87.50,
    'USD',
    'Restaurant - Dinner',
    '2025-02-13 19:45:00',
    '{
        "transaction_id": "TXN000987654321",
        "transaction_type": "PURCHASE",
        "amount": 87.50,
        "currency": "USD",
        "timestamp": "2025-02-13T19:45:00",
        "customer": {
            "customer_id": "CUST005678",
            "name": "Sarah Johnson",
            "email": "sarah.j@example.com",
            "phone": "+1-555-9876",
            "address": {
                "street": "456 Park Avenue",
                "city": "New York",
                "state": "NY",
                "postal_code": "10022",
                "country": "USA"
            },
            "loyalty_tier": "PLATINUM"
        },
        "merchant": {
            "merchant_id": "MERCH9012",
            "name": "Fine Dining Restaurant",
            "category": "Restaurant",
            "mcc": "5812"
        },
        "payment_method": {
            "type": "CREDIT_CARD",
            "last_four": "8765",
            "brand": "AMEX",
            "expiry_month": 6,
            "expiry_year": 2026
        },
        "items": [
            {
                "item_id": "MENU001",
                "name": "Steak Dinner",
                "quantity": 1,
                "unit_price": 45.00,
                "total_price": 45.00,
                "category": "Entrees"
            },
            {
                "item_id": "MENU002",
                "name": "Caesar Salad",
                "quantity": 1,
                "unit_price": 12.00,
                "total_price": 12.00,
                "category": "Appetizers"
            },
            {
                "item_id": "MENU003",
                "name": "Wine",
                "quantity": 2,
                "unit_price": 15.00,
                "total_price": 30.00,
                "category": "Beverages"
            }
        ],
        "metadata": {
            "ip_address": "10.0.5.42",
            "user_agent": "Mobile App iOS 17.2",
            "session_id": "sess_mobile_789",
            "table_number": "12",
            "tip_percentage": 20
        },
        "risk_score": 5.2,
        "status": "COMPLETED"
    }'::jsonb,
    'PENDING'
);

-- Example 3: High-risk transaction (flagged for review)
INSERT INTO enhanced_detail_records (
    master_id,
    record_type,
    account_number,
    customer_name,
    amount,
    currency,
    description,
    transaction_date,
    transaction_data,
    processing_status
) VALUES (
    999,
    'PURCHASE',
    'ACC0099999999',
    'Unknown Customer',
    5000.00,
    'USD',
    'Large electronics purchase',
    '2025-02-13 02:15:00',
    '{
        "transaction_id": "TXN000HIGHRISK",
        "transaction_type": "PURCHASE",
        "amount": 5000.00,
        "currency": "USD",
        "timestamp": "2025-02-13T02:15:00",
        "customer": {
            "customer_id": "CUST999999",
            "name": "Unknown Customer",
            "email": "temp@tempmail.com",
            "phone": "+1-555-0000",
            "address": {
                "street": "PO Box 1",
                "city": "Unknown",
                "state": "XX",
                "postal_code": "00000",
                "country": "Unknown"
            },
            "loyalty_tier": "BRONZE"
        },
        "merchant": {
            "merchant_id": "MERCH0001",
            "name": "Discount Electronics",
            "category": "Electronics",
            "mcc": "5732"
        },
        "payment_method": {
            "type": "CREDIT_CARD",
            "last_four": "0000",
            "brand": "VISA",
            "expiry_month": 12,
            "expiry_year": 2025
        },
        "items": [
            {
                "item_id": "ITEM999",
                "name": "High-end Laptop",
                "quantity": 5,
                "unit_price": 1000.00,
                "total_price": 5000.00,
                "category": "Computers"
            }
        ],
        "metadata": {
            "ip_address": "203.0.113.42",
            "user_agent": "curl/7.68.0",
            "session_id": "sess_suspicious",
            "vpn_detected": true,
            "first_time_customer": true
        },
        "risk_score": 95.7,
        "status": "PENDING"
    }'::jsonb,
    'PENDING'
);

-- ============================================================================
-- STEP 3: Query JSONB data (what the Java app will do)
-- ============================================================================

-- View the complete JSONB structure
SELECT 
    detail_id,
    jsonb_pretty(transaction_data) as formatted_json
FROM enhanced_detail_records
WHERE master_id = 999
LIMIT 1;

-- Extract specific fields (demonstrates what Jackson will unmarshal)
SELECT 
    detail_id,
    transaction_data->>'transaction_id' as txn_id,
    transaction_data->>'transaction_type' as txn_type,
    transaction_data->'customer'->>'customer_id' as customer_id,
    transaction_data->'customer'->>'name' as customer_name,
    transaction_data->'customer'->>'email' as customer_email,
    transaction_data->'customer'->'address'->>'city' as city,
    transaction_data->'merchant'->>'name' as merchant_name,
    transaction_data->'merchant'->>'category' as merchant_category,
    transaction_data->'payment_method'->>'type' as payment_type,
    transaction_data->'payment_method'->>'brand' as payment_brand,
    jsonb_array_length(transaction_data->'items') as item_count,
    (transaction_data->>'risk_score')::NUMERIC as risk_score,
    transaction_data->>'status' as status
FROM enhanced_detail_records
WHERE master_id = 999;

-- ============================================================================
-- STEP 4: What the Java application does
-- ============================================================================

/*
When the Java application processes master_id = 999:

1. EnhancedDetailRecordRepository.streamByMasterId(999)
   - Executes SELECT query
   - For each row:
     - Reads transaction_data as PGobject
     - Extracts JSON string from PGobject
     - Uses Jackson ObjectMapper to unmarshal to TransactionData object
     
2. EnhancedFileWriterService.flattenRecord(record)
   - Takes the unmarshalled TransactionData object
   - Extracts nested fields:
     * customer.customer_id → customerId
     * customer.email → customerEmail
     * customer.address.city → customerCity
     * merchant.name → merchantName
     * payment_method.type → paymentType
     * items.length → itemCount
   - Creates EnhancedDetailOutput with flattened data
   
3. BeanIO writes to file:
   HEADER|999|NYC|20250213|3|2.0
   DETAIL|1|ACC0012345678|John Smith|1299.99|USD|Online purchase...|20250213143000|TXN000123456789|PURCHASE|CUST001234|john.smith@example.com|+1-555-0123|New York|NY|USA|MERCH5678|TechStore Online|Electronics|CREDIT_CARD|4532|VISA|15.5|COMPLETED|3
   DETAIL|2|ACC0087654321|Sarah Johnson|87.50|USD|Restaurant...|20250213194500|TXN000987654321|PURCHASE|CUST005678|sarah.j@example.com|+1-555-9876|New York|NY|USA|MERCH9012|Fine Dining Restaurant|Restaurant|CREDIT_CARD|8765|AMEX|5.2|COMPLETED|3
   DETAIL|3|ACC0099999999|Unknown Customer|5000.00|USD|Large electronics...|20250213021500|TXN000HIGHRISK|PURCHASE|CUST999999|temp@tempmail.com|+1-555-0000|Unknown|XX|Unknown|MERCH0001|Discount Electronics|Electronics|CREDIT_CARD|0000|VISA|95.7|PENDING|1
   TRAILER|3|6387.49|38.8|3
*/

-- ============================================================================
-- STEP 5: Query examples showing JSONB index usage
-- ============================================================================

-- Find high-risk transactions (uses expression index)
EXPLAIN ANALYZE
SELECT detail_id, transaction_data->'customer'->>'name' as customer
FROM enhanced_detail_records
WHERE (transaction_data->>'risk_score')::NUMERIC > 80;

-- Find transactions by customer (uses expression index)
EXPLAIN ANALYZE
SELECT detail_id, transaction_data->>'transaction_id' as txn_id
FROM enhanced_detail_records
WHERE transaction_data->>'customer_id' = 'CUST001234';

-- Find completed transactions (uses expression index)
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM enhanced_detail_records
WHERE transaction_data->>'status' = 'COMPLETED';

-- Containment query (uses GIN index)
EXPLAIN ANALYZE
SELECT detail_id
FROM enhanced_detail_records
WHERE transaction_data @> '{"payment_method": {"brand": "VISA"}}'::jsonb;

-- Find transactions with specific merchant category
EXPLAIN ANALYZE
SELECT detail_id, transaction_data->'merchant'->>'name' as merchant
FROM enhanced_detail_records
WHERE transaction_data->'merchant'->>'category' = 'Electronics';

-- ============================================================================
-- STEP 6: Aggregations on JSONB data
-- ============================================================================

-- Average risk score by merchant category
SELECT 
    transaction_data->'merchant'->>'category' as category,
    COUNT(*) as transaction_count,
    AVG((transaction_data->>'risk_score')::NUMERIC)::NUMERIC(5,2) as avg_risk_score,
    SUM((transaction_data->>'amount')::NUMERIC)::NUMERIC(10,2) as total_amount
FROM enhanced_detail_records
WHERE master_id = 999
GROUP BY category
ORDER BY avg_risk_score DESC;

-- Count by payment brand
SELECT 
    transaction_data->'payment_method'->>'brand' as brand,
    COUNT(*) as count
FROM enhanced_detail_records
WHERE master_id = 999
GROUP BY brand;

-- Top customers by transaction amount
SELECT 
    transaction_data->'customer'->>'customer_id' as customer_id,
    transaction_data->'customer'->>'name' as customer_name,
    COUNT(*) as transaction_count,
    SUM((transaction_data->>'amount')::NUMERIC)::NUMERIC(10,2) as total_spent
FROM enhanced_detail_records
WHERE master_id = 999
GROUP BY customer_id, customer_name
ORDER BY total_spent DESC;

-- ============================================================================
-- STEP 7: Update JSONB data (without replacing entire object)
-- ============================================================================

-- Update just the status field
UPDATE enhanced_detail_records
SET transaction_data = jsonb_set(
    transaction_data,
    '{status}',
    '"APPROVED"'
)
WHERE detail_id IN (
    SELECT detail_id 
    FROM enhanced_detail_records 
    WHERE master_id = 999 
    AND (transaction_data->>'risk_score')::NUMERIC < 50
);

-- Add a new field to existing JSONB
UPDATE enhanced_detail_records
SET transaction_data = transaction_data || '{"reviewed_by": "system", "review_date": "2025-02-13"}'::jsonb
WHERE master_id = 999;

-- ============================================================================
-- STEP 8: Verify the data is ready for processing
-- ============================================================================

SELECT 
    m.master_id,
    m.business_center_code,
    m.priority,
    m.status as master_status,
    COUNT(e.detail_id) as detail_count,
    AVG((e.transaction_data->>'risk_score')::NUMERIC)::NUMERIC(5,2) as avg_risk
FROM master_records m
JOIN enhanced_detail_records e ON m.master_id = e.master_id
WHERE m.master_id = 999
GROUP BY m.master_id, m.business_center_code, m.priority, m.status;

-- View summary statistics
SELECT 
    'Ready for processing' as message,
    COUNT(DISTINCT master_id) as master_count,
    COUNT(*) as detail_count,
    SUM((transaction_data->>'amount')::NUMERIC)::NUMERIC(12,2) as total_amount
FROM enhanced_detail_records
WHERE processing_status = 'PENDING';
