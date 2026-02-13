package com.example.repository;

import com.example.model.DetailRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DetailRecordRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final RowMapper<DetailRecord> ROW_MAPPER = new DetailRecordRowMapper();
    
    /**
     * Stream detail records for a given master_id
     * This allows processing millions of records without loading all into memory
     */
    public Stream<DetailRecord> streamByMasterId(Long masterId, int fetchSize) {
        String sql = """
            SELECT detail_id, master_id, record_type, account_number, customer_name,
                   amount, currency, description, transaction_date, created_at
            FROM detail_records
            WHERE master_id = ?
            ORDER BY detail_id ASC
            """;
        
        log.info("Starting to stream detail records for master_id: {}", masterId);
        
        // Use stream with fetch size for memory-efficient processing
        return jdbcTemplate.queryForStream(sql, ROW_MAPPER, masterId);
    }
    
    /**
     * Get count of detail records for a master_id
     */
    public long countByMasterId(Long masterId) {
        String sql = "SELECT COUNT(*) FROM detail_records WHERE master_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, masterId);
        return count != null ? count : 0L;
    }
    
    private static class DetailRecordRowMapper implements RowMapper<DetailRecord> {
        @Override
        public DetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DetailRecord.builder()
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
                    .build();
        }
    }
}
