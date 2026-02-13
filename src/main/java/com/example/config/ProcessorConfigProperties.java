package com.example.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "processor")
public class ProcessorConfigProperties {
    
    /**
     * Map of business center code to priority (higher = more important)
     */
    private Map<String, Integer> businessCenterPriorities = new HashMap<>();
    
    /**
     * Number of records to fetch in a single batch
     */
    private int batchSize = 1000;
    
    /**
     * Lock timeout in seconds before a master_id lock expires
     */
    private int lockTimeoutSeconds = 300;
    
    /**
     * Polling interval in seconds to check for new work
     */
    private int pollIntervalSeconds = 5;
    
    /**
     * Maximum number of master_ids to process concurrently
     */
    private int maxConcurrentMasters = 10;
    
    /**
     * Output directory for generated files
     */
    private String outputDirectory = "./output";
    
    /**
     * Get priority for a business center code
     * @param businessCenterCode the business center code
     * @return priority (higher = more important), default 0 if not configured
     */
    public int getPriority(String businessCenterCode) {
        return businessCenterPriorities.getOrDefault(businessCenterCode, 0);
    }
}
