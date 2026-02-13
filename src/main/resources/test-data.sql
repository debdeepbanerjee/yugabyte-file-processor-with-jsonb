-- Sample Test Data Generation Script for YugabyteDB
-- This script generates test data for development and testing purposes

-- Function to generate random business center codes based on configuration
CREATE OR REPLACE FUNCTION random_business_center()
RETURNS VARCHAR(10) AS $$
DECLARE
    centers VARCHAR(10)[] := ARRAY['NYC', 'LON', 'TKY', 'HKG', 'SIN', 'CHI', 'LAX', 'SFO', 'BOS', 'MIA'];
BEGIN
    RETURN centers[1 + floor(random() * array_length(centers, 1))::INT];
END;
$$ LANGUAGE plpgsql;

-- Function to map business center to priority
CREATE OR REPLACE FUNCTION get_priority(bc_code VARCHAR)
RETURNS INT AS $$
BEGIN
    RETURN CASE bc_code
        WHEN 'NYC' THEN 100
        WHEN 'LON' THEN 90
        WHEN 'TKY' THEN 80
        WHEN 'HKG' THEN 70
        WHEN 'SIN' THEN 60
        WHEN 'CHI' THEN 50
        WHEN 'LAX' THEN 40
        WHEN 'SFO' THEN 30
        WHEN 'BOS' THEN 20
        WHEN 'MIA' THEN 10
        ELSE 0
    END;
END;
$$ LANGUAGE plpgsql;

-- Clear existing test data (optional - comment out if you want to keep existing data)
-- TRUNCATE TABLE detail_records;
-- TRUNCATE TABLE master_records RESTART IDENTITY CASCADE;

-- Generate master records
-- Adjust the number in generate_series() to create more/fewer masters
DO $$
DECLARE
    bc_code VARCHAR(10);
    rec_count INT := 0;
BEGIN
    FOR i IN 1..50 LOOP
        bc_code := random_business_center();
        
        INSERT INTO master_records (
            business_center_code, 
            priority, 
            status, 
            created_at
        )
        VALUES (
            bc_code,
            get_priority(bc_code),
            'PENDING',
            CURRENT_TIMESTAMP - (random() * INTERVAL '30 days')
        );
        
        rec_count := rec_count + 1;
    END LOOP;
    
    RAISE NOTICE 'Generated % master records', rec_count;
END $$;

-- Generate detail records for each master
-- This creates between 500 and 2000 detail records per master
DO $$
DECLARE
    m_record RECORD;
    detail_count INT;
    total_details BIGINT := 0;
BEGIN
    FOR m_record IN 
        SELECT master_id FROM master_records WHERE status = 'PENDING'
    LOOP
        -- Random number of details between 500 and 2000
        detail_count := 500 + floor(random() * 1500)::INT;
        
        INSERT INTO detail_records (
            master_id,
            record_type,
            account_number,
            customer_name,
            amount,
            currency,
            description,
            transaction_date,
            created_at
        )
        SELECT 
            m_record.master_id,
            CASE (random() * 3)::INT
                WHEN 0 THEN 'DEBIT'
                WHEN 1 THEN 'CREDIT'
                WHEN 2 THEN 'TRANSFER'
                ELSE 'PAYMENT'
            END,
            'ACC' || LPAD((random() * 999999999)::BIGINT::TEXT, 10, '0'),
            CASE (random() * 5)::INT
                WHEN 0 THEN 'John ' || (random_words(1))
                WHEN 1 THEN 'Jane ' || (random_words(1))
                WHEN 2 THEN 'Company ' || (random() * 1000)::INT
                WHEN 3 THEN 'Corp ' || chr(65 + (random() * 26)::INT)
                ELSE 'Customer ' || (random() * 10000)::INT
            END,
            (random() * 50000 - 10000)::DECIMAL(18,2), -- Amounts between -10000 and 40000
            CASE (random() * 4)::INT
                WHEN 0 THEN 'USD'
                WHEN 1 THEN 'EUR'
                WHEN 2 THEN 'GBP'
                ELSE 'JPY'
            END,
            'Transaction reference ' || md5(random()::TEXT),
            CURRENT_TIMESTAMP - (random() * INTERVAL '365 days'),
            CURRENT_TIMESTAMP
        FROM generate_series(1, detail_count);
        
        total_details := total_details + detail_count;
        
        IF m_record.master_id % 10 = 0 THEN
            RAISE NOTICE 'Generated details for master_id %, total so far: %', 
                m_record.master_id, total_details;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'Total detail records generated: %', total_details;
END $$;

-- Helper function for generating random words (used in customer names)
CREATE OR REPLACE FUNCTION random_words(word_count INT)
RETURNS TEXT AS $$
DECLARE
    words TEXT[] := ARRAY[
        'Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Miller', 
        'Davis', 'Rodriguez', 'Martinez', 'Hernandez', 'Lopez', 'Gonzalez',
        'Wilson', 'Anderson', 'Thomas', 'Taylor', 'Moore', 'Jackson', 'Martin'
    ];
    result TEXT := '';
    i INT;
BEGIN
    FOR i IN 1..word_count LOOP
        IF i > 1 THEN
            result := result || ' ';
        END IF;
        result := result || words[1 + floor(random() * array_length(words, 1))::INT];
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Show summary of generated data
SELECT 
    business_center_code,
    priority,
    COUNT(*) as master_count,
    SUM((SELECT COUNT(*) FROM detail_records d WHERE d.master_id = m.master_id)) as total_details
FROM master_records m
WHERE status = 'PENDING'
GROUP BY business_center_code, priority
ORDER BY priority DESC;

-- Show overall statistics
SELECT 
    'Master Records' as record_type,
    COUNT(*)::TEXT as count
FROM master_records
WHERE status = 'PENDING'
UNION ALL
SELECT 
    'Detail Records' as record_type,
    COUNT(*)::TEXT as count
FROM detail_records;
