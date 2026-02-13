package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Model representing complex transaction data stored as JSONB in YugabyteDB
 * This demonstrates how to unmarshal binary JSON from the database into a Java object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionData {
    
    @JsonProperty("transaction_id")
    private String transactionId;
    
    @JsonProperty("transaction_type")
    private String transactionType;
    
    @JsonProperty("amount")
    private BigDecimal amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
    
    @JsonProperty("customer")
    private Customer customer;
    
    @JsonProperty("merchant")
    private Merchant merchant;
    
    @JsonProperty("payment_method")
    private PaymentMethod paymentMethod;
    
    @JsonProperty("items")
    private List<LineItem> items;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    @JsonProperty("risk_score")
    private Double riskScore;
    
    @JsonProperty("status")
    private String status;
    
    // Nested classes for complex JSON structures
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Customer {
        @JsonProperty("customer_id")
        private String customerId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("email")
        private String email;
        
        @JsonProperty("phone")
        private String phone;
        
        @JsonProperty("address")
        private Address address;
        
        @JsonProperty("loyalty_tier")
        private String loyaltyTier;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        @JsonProperty("street")
        private String street;
        
        @JsonProperty("city")
        private String city;
        
        @JsonProperty("state")
        private String state;
        
        @JsonProperty("postal_code")
        private String postalCode;
        
        @JsonProperty("country")
        private String country;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Merchant {
        @JsonProperty("merchant_id")
        private String merchantId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("category")
        private String category;
        
        @JsonProperty("mcc")
        private String mcc; // Merchant Category Code
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethod {
        @JsonProperty("type")
        private String type; // CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, etc.
        
        @JsonProperty("last_four")
        private String lastFour;
        
        @JsonProperty("brand")
        private String brand; // VISA, MASTERCARD, AMEX, etc.
        
        @JsonProperty("expiry_month")
        private Integer expiryMonth;
        
        @JsonProperty("expiry_year")
        private Integer expiryYear;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        @JsonProperty("item_id")
        private String itemId;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("quantity")
        private Integer quantity;
        
        @JsonProperty("unit_price")
        private BigDecimal unitPrice;
        
        @JsonProperty("total_price")
        private BigDecimal totalPrice;
        
        @JsonProperty("category")
        private String category;
    }
}
