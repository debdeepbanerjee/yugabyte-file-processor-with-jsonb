package com.example.repository;

import com.example.model.MasterRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MasterRecordRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final RowMapper<MasterRecord> ROW_MAPPER = new MasterRecordRowMapper();
    
    /**
     * Find next available master_id to process based on priority
     * Uses pessimistic locking (FOR UPDATE SKIP LOCKED) to avoid contention
     */
    public Optional<Long> findNextAvailableMasterId(String instanceId, int lockTimeoutSeconds) {
        String sql = """
            SELECT m.master_id
            FROM master_records m
            WHERE m.status = 'PENDING'
              AND (m.locked_by IS NULL 
                   OR m.locked_at < NOW() - INTERVAL '%d seconds')
            ORDER BY m.priority DESC, m.created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """.formatted(lockTimeoutSeconds);
        
        try {
            List<Long> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("master_id"));
            
            if (!results.isEmpty()) {
                Long masterId = results.get(0);
                // Immediately lock it
                if (lockMaster(masterId, instanceId)) {
                    return Optional.of(masterId);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error finding next available master_id", e);
            return Optional.empty();
        }
    }
    
    /**
     * Lock a master record for processing
     */
    public boolean lockMaster(Long masterId, String instanceId) {
        String sql = """
            UPDATE master_records
            SET locked_by = ?,
                locked_at = ?,
                status = 'PROCESSING',
                updated_at = ?
            WHERE master_id = ?
              AND (locked_by IS NULL OR locked_by = ?)
            """;
        
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(sql, instanceId, now, now, masterId, instanceId);
        
        if (updated > 0) {
            log.info("Successfully locked master_id: {} by instance: {}", masterId, instanceId);
            return true;
        }
        
        log.warn("Failed to lock master_id: {} by instance: {}", masterId, instanceId);
        return false;
    }
    
    /**
     * Unlock and mark master as completed
     */
    public void completeMaster(Long masterId, String instanceId) {
        String sql = """
            UPDATE master_records
            SET locked_by = NULL,
                locked_at = NULL,
                status = 'COMPLETED',
                updated_at = ?
            WHERE master_id = ?
              AND locked_by = ?
            """;
        
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(sql, now, masterId, instanceId);
        
        if (updated > 0) {
            log.info("Completed master_id: {} by instance: {}", masterId, instanceId);
        } else {
            log.warn("Failed to complete master_id: {} - not locked by instance: {}", masterId, instanceId);
        }
    }
    
    /**
     * Unlock and mark master as failed
     */
    public void failMaster(Long masterId, String instanceId, String errorMessage) {
        String sql = """
            UPDATE master_records
            SET locked_by = NULL,
                locked_at = NULL,
                status = 'FAILED',
                error_message = ?,
                updated_at = ?
            WHERE master_id = ?
              AND locked_by = ?
            """;
        
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(sql, errorMessage, now, masterId, instanceId);
        
        if (updated > 0) {
            log.error("Failed master_id: {} by instance: {} - Error: {}", masterId, instanceId, errorMessage);
        }
    }
    
    /**
     * Get master record by ID
     */
    public Optional<MasterRecord> findById(Long masterId) {
        String sql = """
            SELECT master_id, business_center_code, status, locked_by, locked_at, 
                   created_at, updated_at
            FROM master_records
            WHERE master_id = ?
            """;
        
        try {
            List<MasterRecord> results = jdbcTemplate.query(sql, ROW_MAPPER, masterId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Error finding master_id: {}", masterId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Update priority based on business center code
     */
    public void updatePriority(Long masterId, int priority) {
        String sql = """
            UPDATE master_records
            SET priority = ?,
                updated_at = ?
            WHERE master_id = ?
            """;
        
        jdbcTemplate.update(sql, priority, LocalDateTime.now(), masterId);
    }
    
    private static class MasterRecordRowMapper implements RowMapper<MasterRecord> {
        @Override
        public MasterRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return MasterRecord.builder()
                    .masterId(rs.getLong("master_id"))
                    .businessCenterCode(rs.getString("business_center_code"))
                    .status(rs.getString("status"))
                    .lockedBy(rs.getString("locked_by"))
                    .lockedAt(rs.getTimestamp("locked_at") != null 
                            ? rs.getTimestamp("locked_at").toLocalDateTime() 
                            : null)
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at") != null 
                            ? rs.getTimestamp("updated_at").toLocalDateTime() 
                            : null)
                    .build();
        }
    }
}
