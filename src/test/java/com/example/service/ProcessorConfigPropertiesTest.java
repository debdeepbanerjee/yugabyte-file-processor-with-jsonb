package com.example.service;

import com.example.config.ProcessorConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorConfigPropertiesTest {
    
    private ProcessorConfigProperties config;
    
    @BeforeEach
    void setUp() {
        config = new ProcessorConfigProperties();
        
        Map<String, Integer> priorities = new HashMap<>();
        priorities.put("NYC", 100);
        priorities.put("LON", 90);
        priorities.put("TKY", 80);
        
        config.setBusinessCenterPriorities(priorities);
    }
    
    @Test
    void testGetPriorityForConfiguredCenter() {
        assertEquals(100, config.getPriority("NYC"));
        assertEquals(90, config.getPriority("LON"));
        assertEquals(80, config.getPriority("TKY"));
    }
    
    @Test
    void testGetPriorityForUnconfiguredCenter() {
        // Should return default priority of 0
        assertEquals(0, config.getPriority("UNKNOWN"));
        assertEquals(0, config.getPriority("XXX"));
    }
    
    @Test
    void testDefaultValues() {
        ProcessorConfigProperties defaultConfig = new ProcessorConfigProperties();
        
        assertEquals(1000, defaultConfig.getBatchSize());
        assertEquals(300, defaultConfig.getLockTimeoutSeconds());
        assertEquals(5, defaultConfig.getPollIntervalSeconds());
        assertEquals(10, defaultConfig.getMaxConcurrentMasters());
        assertEquals("./output", defaultConfig.getOutputDirectory());
    }
}
