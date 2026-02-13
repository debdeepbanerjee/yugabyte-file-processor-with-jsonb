package com.example.service;

import com.example.config.ProcessorConfigProperties;
import com.example.model.EnhancedDetailRecord;
import com.example.model.MasterRecord;
import com.example.repository.EnhancedDetailRecordRepository;
import com.example.repository.MasterRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Enhanced processing service that reads JSONB data from YugabyteDB
 * and writes flattened records to file
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedRecordProcessingService {
    
    private final MasterRecordRepository masterRecordRepository;
    private final EnhancedDetailRecordRepository enhancedDetailRecordRepository;
    private final EnhancedFileWriterService enhancedFileWriterService;
    private final ProcessorConfigProperties config;
    
    // Unique instance identifier
    private final String instanceId = generateInstanceId();
    
    /**
     * Process a single master record with enhanced JSONB data
     * Reads JSONB from database, unmarshals to Java objects, and writes to file
     */
    public boolean processNextEnhancedMaster() {
        try {
            // Find and lock next available master based on priority
            var masterIdOpt = masterRecordRepository.findNextAvailableMasterId(
                    instanceId, 
                    config.getLockTimeoutSeconds()
            );
            
            if (masterIdOpt.isEmpty()) {
                log.debug("No available enhanced master records to process");
                return false;
            }
            
            Long masterId = masterIdOpt.get();
            log.info("Processing enhanced master_id: {} on instance: {}", masterId, instanceId);
            
            // Get master record details
            MasterRecord master = masterRecordRepository.findById(masterId)
                    .orElseThrow(() -> new RuntimeException("Master record not found: " + masterId));
            
            // Process the master record with JSONB data
            processEnhancedMaster(master);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing enhanced master record", e);
            return false;
        }
    }
    
    /**
     * Process a specific master record with JSONB data
     */
    private void processEnhancedMaster(MasterRecord master) {
        Long masterId = master.getMasterId();
        
        try {
            // Get count for logging
            long recordCount = enhancedDetailRecordRepository.countByMasterId(masterId);
            log.info("Processing enhanced master_id: {} with {} detail records containing JSONB", 
                    masterId, recordCount);
            
            // Stream enhanced detail records with JSONB unmarshalling
            Stream<EnhancedDetailRecord> detailStream = 
                    enhancedDetailRecordRepository.streamByMasterId(
                            masterId, 
                            config.getBatchSize()
                    );
            
            // Generate output file path
            Path outputPath = enhancedFileWriterService.generateEnhancedOutputPath(
                    config.getOutputDirectory(),
                    masterId,
                    master.getBusinessCenterCode()
            );
            
            // Write file using BeanIO - flattens JSONB data
            Path writtenFile = enhancedFileWriterService.writeEnhancedFile(
                    master, 
                    detailStream, 
                    outputPath
            );
            
            // Mark as completed
            masterRecordRepository.completeMaster(masterId, instanceId);
            
            log.info("Successfully completed enhanced master_id: {}, file written to: {}", 
                    masterId, writtenFile);
            
        } catch (Exception e) {
            log.error("Failed to process enhanced master_id: {}", masterId, e);
            masterRecordRepository.failMaster(masterId, instanceId, e.getMessage());
            throw new RuntimeException("Failed to process enhanced master: " + masterId, e);
        }
    }
    
    /**
     * Generate unique instance identifier
     */
    private String generateInstanceId() {
        String hostname = getHostname();
        long processId = ProcessHandle.current().pid();
        long threadId = Thread.currentThread().threadId();
        
        return String.format("%s-enhanced-%d-%d-%d", 
                hostname, 
                processId, 
                threadId,
                System.currentTimeMillis());
    }
    
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    public String getInstanceId() {
        return instanceId;
    }
}
