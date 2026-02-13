package com.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailRecord {
    private Long detailId;
    private Long masterId;
    private String recordType;
    private String accountNumber;
    private String customerName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;
}
