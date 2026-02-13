package com.example.beanio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Enhanced file header with version information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedFileHeader {
    private String recordType;
    private Long masterId;
    private String businessCenterCode;
    private LocalDate fileDate;
    private Long recordCount;
    private String fileVersion; // e.g., "2.0"
}

/**
 * Enhanced file trailer with additional statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedFileTrailer {
    private String recordType;
    private Long totalRecords;
    private BigDecimal totalAmount;
    private Double averageRiskScore;
    private Long uniqueCustomers;
}
