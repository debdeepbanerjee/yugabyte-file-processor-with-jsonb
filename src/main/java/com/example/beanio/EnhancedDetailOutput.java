package com.example.beanio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BeanIO output model for enhanced detail records
 * Flattens the nested JSON structure into a single record for file output
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedDetailOutput {
    // Basic fields
    private String recordType;
    private Long detailId;
    private String accountNumber;
    private String customerName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime transactionDate;
    
    // Flattened JSON fields from TransactionData
    private String transactionId;
    private String transactionType;
    
    // Customer fields
    private String customerId;
    private String customerEmail;
    private String customerPhone;
    private String customerCity;
    private String customerState;
    private String customerCountry;
    
    // Merchant fields
    private String merchantId;
    private String merchantName;
    private String merchantCategory;
    
    // Payment fields
    private String paymentType;
    private String paymentLastFour;
    private String paymentBrand;
    
    // Risk and status
    private Double riskScore;
    private String status;
    
    // Aggregated fields
    private Integer itemCount;
}
