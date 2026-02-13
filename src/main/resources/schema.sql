-- Schema for YugabyteDB (PostgreSQL compatible)

-- Master Records Table
-- Stores the master records with business center code and locking mechanism
CREATE TABLE IF NOT EXISTS master_records (
    master_id BIGSERIAL PRIMARY KEY,
    business_center_code VARCHAR(10) NOT NULL,
    priority INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    locked_by VARCHAR(255),
    locked_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create index on priority and status for efficient querying
CREATE INDEX IF NOT EXISTS idx_master_priority_status 
    ON master_records(priority DESC, created_at ASC) 
    WHERE status = 'PENDING';

-- Create index on business center code
CREATE INDEX IF NOT EXISTS idx_master_business_center 
    ON master_records(business_center_code);

-- Create index on locked_by and locked_at for cleanup queries
CREATE INDEX IF NOT EXISTS idx_master_locks 
    ON master_records(locked_by, locked_at) 
    WHERE locked_by IS NOT NULL;

-- Detail Records Table
-- Stores the detail records associated with each master
-- Partitioned by master_id for better performance with large datasets
CREATE TABLE IF NOT EXISTS detail_records (
    detail_id BIGSERIAL,
    master_id BIGINT NOT NULL,
    record_type VARCHAR(20) NOT NULL,
    account_number VARCHAR(50),
    customer_name VARCHAR(200),
    amount DECIMAL(18, 2),
    currency VARCHAR(3),
    description TEXT,
    transaction_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (master_id, detail_id)
) PARTITION BY HASH (master_id);

-- Create partitions for detail_records (YugabyteDB supports hash partitioning)
-- You can create more partitions based on your data volume
CREATE TABLE IF NOT EXISTS detail_records_p0 PARTITION OF detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);

CREATE TABLE IF NOT EXISTS detail_records_p1 PARTITION OF detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);

CREATE TABLE IF NOT EXISTS detail_records_p2 PARTITION OF detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 2);

CREATE TABLE IF NOT EXISTS detail_records_p3 PARTITION OF detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 3);

-- Create index on master_id for efficient querying within partitions
CREATE INDEX IF NOT EXISTS idx_detail_master_id 
    ON detail_records(master_id, detail_id);

-- Enhanced Detail Records Table with JSONB column
-- Stores detail records with complex transaction data as binary JSON
CREATE TABLE IF NOT EXISTS enhanced_detail_records (
    detail_id BIGSERIAL,
    master_id BIGINT NOT NULL,
    record_type VARCHAR(20) NOT NULL,
    account_number VARCHAR(50),
    customer_name VARCHAR(200),
    amount DECIMAL(18, 2),
    currency VARCHAR(3),
    description TEXT,
    transaction_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- JSONB column for complex transaction data
    -- This is stored as binary JSON for efficient querying and indexing
    transaction_data JSONB,
    
    processing_status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    
    PRIMARY KEY (master_id, detail_id)
) PARTITION BY HASH (master_id);

-- Create partitions for enhanced_detail_records
CREATE TABLE IF NOT EXISTS enhanced_detail_records_p0 PARTITION OF enhanced_detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);

CREATE TABLE IF NOT EXISTS enhanced_detail_records_p1 PARTITION OF enhanced_detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);

CREATE TABLE IF NOT EXISTS enhanced_detail_records_p2 PARTITION OF enhanced_detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 2);

CREATE TABLE IF NOT EXISTS enhanced_detail_records_p3 PARTITION OF enhanced_detail_records
    FOR VALUES WITH (MODULUS 4, REMAINDER 3);

-- Create index on master_id for enhanced records
CREATE INDEX IF NOT EXISTS idx_enhanced_detail_master_id 
    ON enhanced_detail_records(master_id, detail_id);

-- Create GIN index on JSONB column for efficient JSON queries
-- This allows fast queries like: WHERE transaction_data @> '{"status": "completed"}'
CREATE INDEX IF NOT EXISTS idx_enhanced_detail_jsonb 
    ON enhanced_detail_records USING GIN (transaction_data);

-- Create specific JSONB path indexes for commonly queried fields
CREATE INDEX IF NOT EXISTS idx_enhanced_detail_customer_id 
    ON enhanced_detail_records ((transaction_data->>'customer_id'));

CREATE INDEX IF NOT EXISTS idx_enhanced_detail_status 
    ON enhanced_detail_records ((transaction_data->>'status'));

CREATE INDEX IF NOT EXISTS idx_enhanced_detail_risk_score 
    ON enhanced_detail_records (((transaction_data->>'risk_score')::NUMERIC));

-- Sample data insertion script (for testing)
-- Uncomment to populate with test data

/*
-- Insert sample master records
INSERT INTO master_records (business_center_code, priority, status)
VALUES 
    ('NYC', 100, 'PENDING'),
    ('LON', 90, 'PENDING'),
    ('TKY', 80, 'PENDING'),
    ('HKG', 70, 'PENDING'),
    ('SIN', 60, 'PENDING');

-- Insert sample detail records (you would typically have many more)
INSERT INTO detail_records (master_id, record_type, account_number, customer_name, amount, currency, description, transaction_date)
SELECT 
    m.master_id,
    'TXN',
    'ACC' || LPAD((RANDOM() * 1000000)::INT::TEXT, 10, '0'),
    'Customer ' || (RANDOM() * 1000)::INT,
    (RANDOM() * 10000)::DECIMAL(18,2),
    'USD',
    'Transaction ' || generate_series,
    CURRENT_TIMESTAMP - (RANDOM() * INTERVAL '365 days')
FROM master_records m
CROSS JOIN generate_series(1, 1000);
*/

-- Function to clean up stale locks (optional, can be called periodically)
CREATE OR REPLACE FUNCTION cleanup_stale_locks(timeout_seconds INT)
RETURNS INT AS $$
DECLARE
    updated_count INT;
BEGIN
    UPDATE master_records
    SET locked_by = NULL,
        locked_at = NULL,
        status = 'PENDING',
        updated_at = CURRENT_TIMESTAMP
    WHERE locked_at < CURRENT_TIMESTAMP - (timeout_seconds || ' seconds')::INTERVAL
      AND locked_by IS NOT NULL
      AND status = 'PROCESSING';
    
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

-- View for monitoring processing status
CREATE OR REPLACE VIEW processing_status AS
SELECT 
    status,
    COUNT(*) as record_count,
    COUNT(DISTINCT business_center_code) as business_centers,
    COUNT(DISTINCT locked_by) as active_instances
FROM master_records
GROUP BY status;
