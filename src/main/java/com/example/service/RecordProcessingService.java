package com.example.service;

import com.example.config.ProcessorConfigProperties;
import com.example.model.DetailRecord;
import com.example.model.MasterRecord;
import com.example.repository.DetailRecordRepository;
import com.example.repository.MasterRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordProcessingService {
    
    private final MasterRecordRepository masterRecordRepository;
    private final DetailRecordRepository detailRecordRepository;
    private final FileWriterService fileWriterService;
    private final ProcessorConfigProperties config;
    
    // Unique instance identifier
    private final String instanceId = generateInstanceId();
    
    /**
     * Process a single master record - find, lock, process, write file
     */
    public boolean processNextMaster() {
        try {
            // Find and lock next available master based on priority
            var masterIdOpt = masterRecordRepository.findNextAvailableMasterId(
                    instanceId, 
                    config.getLockTimeoutSeconds()
            );
            
            if (masterIdOpt.isEmpty()) {
                log.debug("No available master records to process");
                return false;
            }
            
            Long masterId = masterIdOpt.get();
            log.info("Processing master_id: {} on instance: {}", masterId, instanceId);
            
            // Get master record details
            MasterRecord master = masterRecordRepository.findById(masterId)
                    .orElseThrow(() -> new RuntimeException("Master record not found: " + masterId));
            
            // Process the master record
            processMaster(master);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing master record", e);
            return false;
        }
    }
    
    /**
     * Process a specific master record
     */
    private void processMaster(MasterRecord master) {
        Long masterId = master.getMasterId();
        
        try {
            // Get count for logging
            long recordCount = detailRecordRepository.countByMasterId(masterId);
            log.info("Processing master_id: {} with {} detail records", masterId, recordCount);
            
            // Stream detail records (non-blocking, memory efficient)
            Stream<DetailRecord> detailStream = detailRecordRepository.streamByMasterId(
                    masterId, 
                    config.getBatchSize()
            );
            
            // Generate output file path
            Path outputPath = fileWriterService.generateOutputPath(
                    config.getOutputDirectory(),
                    masterId,
                    master.getBusinessCenterCode()
            );
            
            // Write file using BeanIO
            Path writtenFile = fileWriterService.writeFile(master, detailStream, outputPath);
            
            // Mark as completed
            masterRecordRepository.completeMaster(masterId, instanceId);
            
            log.info("Successfully completed master_id: {}, file written to: {}", 
                    masterId, writtenFile);
            
        } catch (Exception e) {
            log.error("Failed to process master_id: {}", masterId, e);
            masterRecordRepository.failMaster(masterId, instanceId, e.getMessage());
            throw new RuntimeException("Failed to process master: " + masterId, e);
        }
    }
    
    /**
     * Initialize priorities for all pending masters based on business center config
     */
    public void initializePriorities() {
        log.info("Initializing priorities from configuration");
        
        // This could be optimized with a batch update query
        config.getBusinessCenterPriorities().forEach((businessCenter, priority) -> {
            log.debug("Business center: {} has priority: {}", businessCenter, priority);
        });
    }
    
    /**
     * Generate unique instance identifier
     */
    private String generateInstanceId() {
        String hostname = getHostname();
        long processId = ProcessHandle.current().pid();
        long threadId = Thread.currentThread().threadId();
        
        return String.format("%s-%d-%d-%d", 
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
