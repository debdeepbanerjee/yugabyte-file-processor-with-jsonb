package com.example.beanio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTrailer {
    private String recordType;
    private Long totalRecords;
    private BigDecimal totalAmount;
}
