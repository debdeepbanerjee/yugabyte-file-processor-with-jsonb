package com.example.repository;

import com.example.model.EnhancedDetailRecord;
import com.example.model.TransactionData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

/**
 * Repository for reading records with JSONB columns from YugabyteDB
 * Demonstrates how to unmarshal binary JSON into Java objects using Jackson
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EnhancedDetailRecordRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Stream enhanced detail records with JSONB data for a given master_id
     * The transaction_data column is stored as JSONB and unmarshalled to TransactionData object
     */
    public Stream<EnhancedDetailRecord> streamByMasterId(Long masterId, int fetchSize) {
        String sql = """
            SELECT 
                detail_id, 
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
                processing_status,
                error_message
            FROM enhanced_detail_records
            WHERE master_id = ?
            ORDER BY detail_id ASC
            """;
        
        log.info("Starting to stream enhanced detail records with JSONB for master_id: {}", masterId);
        
        // Use stream with fetch size for memory-efficient processing
        return jdbcTemplate.queryForStream(sql, new EnhancedDetailRecordRowMapper(), masterId);
    }
    
    /**
     * Get count of enhanced detail records for a master_id
     */
    public long countByMasterId(Long masterId) {
        String sql = "SELECT COUNT(*) FROM enhanced_detail_records WHERE master_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, masterId);
        return count != null ? count : 0L;
    }
    
    /**
     * RowMapper that handles JSONB column unmarshalling
     */
    private class EnhancedDetailRecordRowMapper implements RowMapper<EnhancedDetailRecord> {
        @Override
        public EnhancedDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            EnhancedDetailRecord record = EnhancedDetailRecord.builder()
                    .detailId(rs.getLong("detail_id"))
                    .masterId(rs.getLong("master_id"))
                    .recordType(rs.getString("record_type"))
                    .accountNumber(rs.getString("account_number"))
                    .customerName(rs.getString("customer_name"))
                    .amount(rs.getBigDecimal("amount"))
                    .currency(rs.getString("currency"))
                    .description(rs.getString("description"))
                    .transactionDate(rs.getTimestamp("transaction_date") != null
                            ? rs.getTimestamp("transaction_date").toLocalDateTime()
                            : null)
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .processingStatus(rs.getString("processing_status"))
                    .errorMessage(rs.getString("error_message"))
                    .build();
            
            // Unmarshal JSONB data
            Object jsonbObject = rs.getObject("transaction_data");
            if (jsonbObject != null) {
                try {
                    String jsonString;
                    
                    // Handle PGobject (PostgreSQL JSONB type)
                    if (jsonbObject instanceof PGobject pgObject) {
                        jsonString = pgObject.getValue();
                    } else {
                        jsonString = jsonbObject.toString();
                    }
                    
                    if (jsonString != null && !jsonString.isEmpty()) {
                        // Unmarshal JSON string to TransactionData object
                        TransactionData transactionData = objectMapper.readValue(
                                jsonString, 
                                TransactionData.class
                        );
                        record.setTransactionData(transactionData);
                        
                        log.debug("Unmarshalled JSONB for detail_id: {}, transaction_id: {}", 
                                record.getDetailId(), 
                                transactionData.getTransactionId());
                    }
                } catch (Exception e) {
                    log.error("Failed to unmarshal JSONB for detail_id: {}", 
                            record.getDetailId(), e);
                    // Continue processing - don't fail the entire record
                }
            }
            
            return record;
        }
    }
}
