package com.example.service;

import com.example.beanio.FileHeader;
import com.example.beanio.FileTrailer;
import com.example.model.DetailRecord;
import com.example.model.MasterRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanio.StreamFactory;
import org.beanio.BeanWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileWriterService {
    
    private final StreamFactory streamFactory;
    
    public FileWriterService() {
        // Initialize BeanIO StreamFactory
        this.streamFactory = StreamFactory.newInstance();
        try {
            streamFactory.load(new ClassPathResource("beanio-mapping.xml").getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BeanIO mapping", e);
        }
    }
    
    /**
     * Write detail records to file using BeanIO
     * Uses virtual threads for non-blocking I/O
     */
    public Path writeFile(MasterRecord master, Stream<DetailRecord> detailStream, Path outputPath) 
            throws IOException {
        
        log.info("Starting file write for master_id: {} to path: {}", master.getMasterId(), outputPath);
        
        // Ensure output directory exists
        Files.createDirectories(outputPath.getParent());
        
        // Counters for trailer
        AtomicLong recordCount = new AtomicLong(0);
        AtomicReference<BigDecimal> totalAmount = new AtomicReference<>(BigDecimal.ZERO);
        
        // Use try-with-resources for automatic cleanup
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING);
             BeanWriter beanWriter = streamFactory.createWriter("detailRecordStream", writer)) {
            
            // Write header
            FileHeader header = FileHeader.builder()
                    .recordType("HEADER")
                    .masterId(master.getMasterId())
                    .businessCenterCode(master.getBusinessCenterCode())
                    .fileDate(LocalDate.now())
                    .recordCount(0L) // Will be updated in trailer
                    .build();
            
            beanWriter.write("header", header);
            
            // Write detail records using stream
            detailStream.forEach(detail -> {
                try {
                    beanWriter.write("detail", detail);
                    recordCount.incrementAndGet();
                    
                    // Accumulate total amount
                    if (detail.getAmount() != null) {
                        totalAmount.updateAndGet(current -> 
                            current.add(detail.getAmount())
                        );
                    }
                    
                    // Log progress for large files
                    if (recordCount.get() % 10000 == 0) {
                        log.debug("Written {} records for master_id: {}", 
                                recordCount.get(), master.getMasterId());
                    }
                } catch (Exception e) {
                    log.error("Error writing detail record: {}", detail.getDetailId(), e);
                    throw new RuntimeException("Failed to write detail record", e);
                }
            });
            
            // Write trailer
            FileTrailer trailer = FileTrailer.builder()
                    .recordType("TRAILER")
                    .totalRecords(recordCount.get())
                    .totalAmount(totalAmount.get())
                    .build();
            
            beanWriter.write("trailer", trailer);
            
            beanWriter.flush();
            
            log.info("Completed file write for master_id: {} - {} records written, total amount: {}", 
                    master.getMasterId(), recordCount.get(), totalAmount.get());
        }
        
        return outputPath;
    }
    
    /**
     * Generate output file path
     */
    public Path generateOutputPath(String outputDirectory, Long masterId, String businessCenterCode) {
        String filename = String.format("%s_%d_%s.txt", 
                businessCenterCode,
                masterId,
                System.currentTimeMillis());
        
        return Path.of(outputDirectory, filename);
    }
}
