package com.example.scheduler;

import com.example.config.ProcessorConfigProperties;
import com.example.service.RecordProcessingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingScheduler {
    
    private final RecordProcessingService processingService;
    private final ProcessorConfigProperties config;
    private final AsyncTaskExecutor virtualThreadExecutor;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Semaphore semaphore = new Semaphore(10); // Max concurrent tasks
    
    @PostConstruct
    public void start() {
        running.set(true);
        
        // Initialize priorities
        processingService.initializePriorities();
        
        log.info("Starting processing scheduler on instance: {}", 
                processingService.getInstanceId());
        log.info("Max concurrent masters: {}", config.getMaxConcurrentMasters());
        log.info("Poll interval: {} seconds", config.getPollIntervalSeconds());
        
        // Update semaphore permits based on configuration
        int additionalPermits = config.getMaxConcurrentMasters() - semaphore.availablePermits();
        if (additionalPermits > 0) {
            semaphore.release(additionalPermits);
        }
        
        // Start the main polling loop in a virtual thread
        virtualThreadExecutor.submit(this::pollForWork);
    }
    
    @PreDestroy
    public void stop() {
        log.info("Stopping processing scheduler");
        running.set(false);
    }
    
    /**
     * Main polling loop - runs continuously looking for work
     */
    private void pollForWork() {
        log.info("Starting poll loop");
        
        while (running.get()) {
            try {
                // Try to acquire a permit (limits concurrency)
                if (semaphore.tryAcquire()) {
                    // Submit processing task in a virtual thread
                    virtualThreadExecutor.submit(() -> {
                        try {
                            boolean processed = processingService.processNextMaster();
                            
                            if (!processed) {
                                // No work available, sleep before next poll
                                Thread.sleep(config.getPollIntervalSeconds() * 1000L);
                            }
                            // If work was found and processed, immediately look for more
                            
                        } catch (Exception e) {
                            log.error("Error in processing task", e);
                        } finally {
                            // Release the permit
                            semaphore.release();
                        }
                    });
                } else {
                    // All permits in use, wait before trying again
                    Thread.sleep(1000);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Poll loop interrupted", e);
                break;
            } catch (Exception e) {
                log.error("Error in poll loop", e);
                try {
                    Thread.sleep(5000); // Back off on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("Poll loop stopped");
    }
}
