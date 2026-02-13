-- Enhanced Test Data Generation Script with JSONB
-- Generates test data including complex JSON structures stored as JSONB

-- Helper function to generate random customer JSON
CREATE OR REPLACE FUNCTION generate_customer_json()
RETURNS JSONB AS $$
DECLARE
    cities TEXT[] := ARRAY['New York', 'London', 'Tokyo', 'Hong Kong', 'Singapore', 'Chicago', 'Los Angeles'];
    states TEXT[] := ARRAY['NY', 'CA', 'TX', 'FL', 'IL', 'WA', 'MA'];
    countries TEXT[] := ARRAY['USA', 'UK', 'Japan', 'Hong Kong', 'Singapore'];
    tiers TEXT[] := ARRAY['GOLD', 'SILVER', 'BRONZE', 'PLATINUM'];
BEGIN
    RETURN jsonb_build_object(
        'customer_id', 'CUST' || LPAD((random() * 999999)::INT::TEXT, 6, '0'),
        'name', 'Customer ' || (random() * 1000)::INT,
        'email', 'customer' || (random() * 1000)::INT || '@example.com',
        'phone', '+1-555-' || LPAD((random() * 9999999)::INT::TEXT, 7, '0'),
        'address', jsonb_build_object(
            'street', (random() * 9999)::INT || ' Main St',
            'city', cities[1 + floor(random() * array_length(cities, 1))::INT],
            'state', states[1 + floor(random() * array_length(states, 1))::INT],
            'postal_code', LPAD((random() * 99999)::INT::TEXT, 5, '0'),
            'country', countries[1 + floor(random() * array_length(countries, 1))::INT]
        ),
        'loyalty_tier', tiers[1 + floor(random() * array_length(tiers, 1))::INT]
    );
END;
$$ LANGUAGE plpgsql;

-- Helper function to generate random merchant JSON
CREATE OR REPLACE FUNCTION generate_merchant_json()
RETURNS JSONB AS $$
DECLARE
    categories TEXT[] := ARRAY['Restaurant', 'Retail', 'Grocery', 'Gas Station', 'Online', 'Entertainment'];
BEGIN
    RETURN jsonb_build_object(
        'merchant_id', 'MERCH' || LPAD((random() * 999999)::INT::TEXT, 6, '0'),
        'name', 'Merchant ' || (random() * 1000)::INT,
        'category', categories[1 + floor(random() * array_length(categories, 1))::INT],
        'mcc', LPAD((5000 + (random() * 4999)::INT)::TEXT, 4, '0')
    );
END;
$$ LANGUAGE plpgsql;

-- Helper function to generate random payment method JSON
CREATE OR REPLACE FUNCTION generate_payment_method_json()
RETURNS JSONB AS $$
DECLARE
    types TEXT[] := ARRAY['CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'DIGITAL_WALLET'];
    brands TEXT[] := ARRAY['VISA', 'MASTERCARD', 'AMEX', 'DISCOVER'];
BEGIN
    RETURN jsonb_build_object(
        'type', types[1 + floor(random() * array_length(types, 1))::INT],
        'last_four', LPAD((random() * 9999)::INT::TEXT, 4, '0'),
        'brand', brands[1 + floor(random() * array_length(brands, 1))::INT],
        'expiry_month', 1 + floor(random() * 12)::INT,
        'expiry_year', 2025 + floor(random() * 5)::INT
    );
END;
$$ LANGUAGE plpgsql;

-- Helper function to generate random line items JSON array
CREATE OR REPLACE FUNCTION generate_line_items_json()
RETURNS JSONB AS $$
DECLARE
    item_count INT;
    items JSONB := '[]'::JSONB;
    i INT;
    unit_price NUMERIC;
    quantity INT;
BEGIN
    item_count := 1 + floor(random() * 5)::INT;
    
    FOR i IN 1..item_count LOOP
        quantity := 1 + floor(random() * 10)::INT;
        unit_price := (random() * 1000)::NUMERIC(10,2);
        
        items := items || jsonb_build_object(
            'item_id', 'ITEM' || LPAD((random() * 999999)::INT::TEXT, 6, '0'),
            'name', 'Product ' || (random() * 1000)::INT,
            'quantity', quantity,
            'unit_price', unit_price,
            'total_price', (quantity * unit_price)::NUMERIC(10,2),
            'category', CASE (random() * 4)::INT
                WHEN 0 THEN 'Electronics'
                WHEN 1 THEN 'Clothing'
                WHEN 2 THEN 'Food'
                ELSE 'Other'
            END
        );
    END LOOP;
    
    RETURN items;
END;
$$ LANGUAGE plpgsql;

-- Helper function to generate complete transaction data JSONB
CREATE OR REPLACE FUNCTION generate_transaction_data_json()
RETURNS JSONB AS $$
DECLARE
    txn_types TEXT[] := ARRAY['PURCHASE', 'REFUND', 'VOID', 'AUTHORIZATION'];
    statuses TEXT[] := ARRAY['COMPLETED', 'PENDING', 'FAILED', 'CANCELLED'];
    currencies TEXT[] := ARRAY['USD', 'EUR', 'GBP', 'JPY'];
BEGIN
    RETURN jsonb_build_object(
        'transaction_id', 'TXN' || LPAD((random() * 999999999)::BIGINT::TEXT, 12, '0'),
        'transaction_type', txn_types[1 + floor(random() * array_length(txn_types, 1))::INT],
        'amount', (random() * 10000)::NUMERIC(10,2),
        'currency', currencies[1 + floor(random() * array_length(currencies, 1))::INT],
        'timestamp', (CURRENT_TIMESTAMP - (random() * INTERVAL '365 days'))::TEXT,
        'customer', generate_customer_json(),
        'merchant', generate_merchant_json(),
        'payment_method', generate_payment_method_json(),
        'items', generate_line_items_json(),
        'metadata', jsonb_build_object(
            'ip_address', 
            (floor(random() * 255)::INT)::TEXT || '.' ||
            (floor(random() * 255)::INT)::TEXT || '.' ||
            (floor(random() * 255)::INT)::TEXT || '.' ||
            (floor(random() * 255)::INT)::TEXT,
            'user_agent', 'Mozilla/5.0',
            'session_id', md5(random()::TEXT)
        ),
        'risk_score', (random() * 100)::NUMERIC(5,2),
        'status', statuses[1 + floor(random() * array_length(statuses, 1))::INT]
    );
END;
$$ LANGUAGE plpgsql;

-- Generate enhanced detail records with JSONB data
DO $$
DECLARE
    m_record RECORD;
    detail_count INT;
    total_details BIGINT := 0;
    txn_data JSONB;
    i INT;
BEGIN
    -- For each pending master, create enhanced detail records
    FOR m_record IN 
        SELECT master_id FROM master_records WHERE status = 'PENDING' LIMIT 20
    LOOP
        -- Random number of details between 100 and 500 for testing
        detail_count := 100 + floor(random() * 400)::INT;
        
        FOR i IN 1..detail_count LOOP
            -- Generate transaction data as JSONB
            txn_data := generate_transaction_data_json();
            
            INSERT INTO enhanced_detail_records (
                master_id,
                record_type,
                account_number,
                customer_name,
                amount,
                currency,
                description,
                transaction_date,
                created_at,
                transaction_data,
                processing_status
            )
            VALUES (
                m_record.master_id,
                CASE (random() * 3)::INT
                    WHEN 0 THEN 'DEBIT'
                    WHEN 1 THEN 'CREDIT'
                    WHEN 2 THEN 'TRANSFER'
                    ELSE 'PAYMENT'
                END,
                'ACC' || LPAD((random() * 999999999)::BIGINT::TEXT, 10, '0'),
                txn_data->'customer'->>'name',
                (txn_data->>'amount')::NUMERIC,
                txn_data->>'currency',
                'Transaction reference ' || md5(random()::TEXT),
                (txn_data->>'timestamp')::TIMESTAMP,
                CURRENT_TIMESTAMP,
                txn_data,
                'PENDING'
            );
        END LOOP;
        
        total_details := total_details + detail_count;
        
        RAISE NOTICE 'Generated % JSONB records for master_id %, total so far: %', 
            detail_count, m_record.master_id, total_details;
    END LOOP;
    
    RAISE NOTICE 'Total enhanced detail records with JSONB generated: %', total_details;
END $$;

-- Show sample JSONB data
SELECT 
    detail_id,
    master_id,
    transaction_data->>'transaction_id' as transaction_id,
    transaction_data->>'transaction_type' as transaction_type,
    transaction_data->'customer'->>'name' as customer_name,
    transaction_data->'customer'->>'email' as customer_email,
    transaction_data->'merchant'->>'name' as merchant_name,
    transaction_data->>'status' as status,
    transaction_data->>'risk_score' as risk_score
FROM enhanced_detail_records
LIMIT 5;

-- Show JSONB query examples
RAISE NOTICE 'Example JSONB queries:';

-- Query by customer_id (uses index)
EXPLAIN ANALYZE
SELECT COUNT(*) 
FROM enhanced_detail_records 
WHERE transaction_data->>'customer_id' = 'CUST000001';

-- Query by status (uses index)
EXPLAIN ANALYZE
SELECT COUNT(*) 
FROM enhanced_detail_records 
WHERE transaction_data->>'status' = 'COMPLETED';

-- Query by risk score range (uses index)
EXPLAIN ANALYZE
SELECT COUNT(*) 
FROM enhanced_detail_records 
WHERE (transaction_data->>'risk_score')::NUMERIC > 80;

-- Complex JSONB query with containment operator
EXPLAIN ANALYZE
SELECT detail_id, transaction_data
FROM enhanced_detail_records
WHERE transaction_data @> '{"status": "COMPLETED"}'::JSONB
LIMIT 10;

-- Statistics
SELECT 
    'Enhanced Detail Records' as record_type,
    COUNT(*)::TEXT as count,
    COUNT(DISTINCT master_id)::TEXT as masters,
    AVG((transaction_data->>'risk_score')::NUMERIC)::NUMERIC(5,2) as avg_risk_score
FROM enhanced_detail_records;
