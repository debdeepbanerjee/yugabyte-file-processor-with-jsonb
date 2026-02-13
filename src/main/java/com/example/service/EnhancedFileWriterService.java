package com.example.service;

import com.example.beanio.EnhancedDetailOutput;
import com.example.beanio.EnhancedFileHeader;
import com.example.beanio.EnhancedFileTrailer;
import com.example.model.EnhancedDetailRecord;
import com.example.model.MasterRecord;
import com.example.model.TransactionData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanio.BeanWriter;
import org.beanio.StreamFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Enhanced file writer service that processes JSONB data from YugabyteDB
 * and writes flattened records to file using BeanIO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedFileWriterService {
    
    private final StreamFactory streamFactory;
    
    public EnhancedFileWriterService() {
        // Initialize BeanIO StreamFactory
        this.streamFactory = StreamFactory.newInstance();
        try {
            streamFactory.load(new ClassPathResource("beanio-mapping-enhanced.xml").getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load enhanced BeanIO mapping", e);
        }
    }
    
    /**
     * Write enhanced detail records with JSONB data to file using BeanIO
     * Unmarshals JSONB, flattens nested structure, and writes to delimited file
     */
    public Path writeEnhancedFile(MasterRecord master, 
                                   Stream<EnhancedDetailRecord> detailStream, 
                                   Path outputPath) throws IOException {
        
        log.info("Starting enhanced file write for master_id: {} to path: {}", 
                master.getMasterId(), outputPath);
        
        // Ensure output directory exists
        Files.createDirectories(outputPath.getParent());
        
        // Statistics collectors
        AtomicLong recordCount = new AtomicLong(0);
        AtomicReference<BigDecimal> totalAmount = new AtomicReference<>(BigDecimal.ZERO);
        AtomicReference<Double> totalRiskScore = new AtomicReference<>(0.0);
        AtomicLong riskScoreCount = new AtomicLong(0);
        Set<String> uniqueCustomers = new HashSet<>();
        
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.TRUNCATE_EXISTING);
             BeanWriter beanWriter = streamFactory.createWriter("enhancedDetailRecordStream", writer)) {
            
            // Write header
            EnhancedFileHeader header = EnhancedFileHeader.builder()
                    .recordType("HEADER")
                    .masterId(master.getMasterId())
                    .businessCenterCode(master.getBusinessCenterCode())
                    .fileDate(LocalDate.now())
                    .recordCount(0L) // Will be updated in trailer
                    .fileVersion("2.0")
                    .build();
            
            beanWriter.write("header", header);
            
            // Write detail records - flatten JSONB data
            detailStream.forEach(enhancedRecord -> {
                try {
                    // Flatten the enhanced record with JSONB data
                    EnhancedDetailOutput output = flattenRecord(enhancedRecord);
                    
                    beanWriter.write("detail", output);
                    recordCount.incrementAndGet();
                    
                    // Accumulate statistics
                    if (enhancedRecord.getAmount() != null) {
                        totalAmount.updateAndGet(current -> 
                            current.add(enhancedRecord.getAmount())
                        );
                    }
                    
                    // Track risk scores
                    if (output.getRiskScore() != null) {
                        totalRiskScore.updateAndGet(current -> 
                            current + output.getRiskScore()
                        );
                        riskScoreCount.incrementAndGet();
                    }
                    
                    // Track unique customers
                    if (output.getCustomerId() != null) {
                        uniqueCustomers.add(output.getCustomerId());
                    }
                    
                    // Log progress for large files
                    if (recordCount.get() % 10000 == 0) {
                        log.debug("Written {} enhanced records for master_id: {}", 
                                recordCount.get(), master.getMasterId());
                    }
                } catch (Exception e) {
                    log.error("Error writing enhanced detail record: {}", 
                            enhancedRecord.getDetailId(), e);
                    throw new RuntimeException("Failed to write enhanced detail record", e);
                }
            });
            
            // Calculate average risk score
            Double averageRiskScore = riskScoreCount.get() > 0 
                    ? totalRiskScore.get() / riskScoreCount.get() 
                    : 0.0;
            
            // Write trailer with enhanced statistics
            EnhancedFileTrailer trailer = EnhancedFileTrailer.builder()
                    .recordType("TRAILER")
                    .totalRecords(recordCount.get())
                    .totalAmount(totalAmount.get())
                    .averageRiskScore(
                            BigDecimal.valueOf(averageRiskScore)
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .doubleValue()
                    )
                    .uniqueCustomers((long) uniqueCustomers.size())
                    .build();
            
            beanWriter.write("trailer", trailer);
            beanWriter.flush();
            
            log.info("Completed enhanced file write for master_id: {} - {} records, {} unique customers, avg risk: {}", 
                    master.getMasterId(), 
                    recordCount.get(), 
                    uniqueCustomers.size(),
                    averageRiskScore);
        }
        
        return outputPath;
    }
    
    /**
     * Flatten an EnhancedDetailRecord with JSONB data into a flat output record
     * Extracts nested JSON fields into top-level fields
     */
    private EnhancedDetailOutput flattenRecord(EnhancedDetailRecord record) {
        EnhancedDetailOutput.EnhancedDetailOutputBuilder builder = EnhancedDetailOutput.builder()
                .recordType("DETAIL")
                .detailId(record.getDetailId())
                .accountNumber(record.getAccountNumber())
                .customerName(record.getCustomerName())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .description(record.getDescription())
                .transactionDate(record.getTransactionDate());
        
        // Extract and flatten JSONB data
        TransactionData txnData = record.getTransactionData();
        if (txnData != null) {
            builder.transactionId(txnData.getTransactionId())
                   .transactionType(txnData.getTransactionType())
                   .riskScore(txnData.getRiskScore())
                   .status(txnData.getStatus());
            
            // Customer data
            if (txnData.getCustomer() != null) {
                TransactionData.Customer customer = txnData.getCustomer();
                builder.customerId(customer.getCustomerId())
                       .customerEmail(customer.getEmail())
                       .customerPhone(customer.getPhone());
                
                if (customer.getAddress() != null) {
                    builder.customerCity(customer.getAddress().getCity())
                           .customerState(customer.getAddress().getState())
                           .customerCountry(customer.getAddress().getCountry());
                }
            }
            
            // Merchant data
            if (txnData.getMerchant() != null) {
                TransactionData.Merchant merchant = txnData.getMerchant();
                builder.merchantId(merchant.getMerchantId())
                       .merchantName(merchant.getName())
                       .merchantCategory(merchant.getCategory());
            }
            
            // Payment method data
            if (txnData.getPaymentMethod() != null) {
                TransactionData.PaymentMethod payment = txnData.getPaymentMethod();
                builder.paymentType(payment.getType())
                       .paymentLastFour(payment.getLastFour())
                       .paymentBrand(payment.getBrand());
            }
            
            // Items count
            if (txnData.getItems() != null) {
                builder.itemCount(txnData.getItems().size());
            }
        }
        
        return builder.build();
    }
    
    /**
     * Generate output file path for enhanced files
     */
    public Path generateEnhancedOutputPath(String outputDirectory, 
                                           Long masterId, 
                                           String businessCenterCode) {
        String filename = String.format("%s_%d_enhanced_%s.txt", 
                businessCenterCode,
                masterId,
                System.currentTimeMillis());
        
        return Path.of(outputDirectory, filename);
    }
}
