package com.example.beanio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileHeader {
    private String recordType;
    private Long masterId;
    private String businessCenterCode;
    private LocalDate fileDate;
    private Long recordCount;
}
