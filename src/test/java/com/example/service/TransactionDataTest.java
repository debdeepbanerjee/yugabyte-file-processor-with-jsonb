package com.example.service;

import com.example.model.TransactionData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JSONB unmarshalling and processing
 */
class TransactionDataTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }
    
    @Test
    void testUnmarshalSimpleTransactionData() throws Exception {
        String json = """
            {
                "transaction_id": "TXN123",
                "transaction_type": "PURCHASE",
                "amount": 100.50,
                "currency": "USD",
                "risk_score": 25.5,
                "status": "COMPLETED"
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData);
        assertEquals("TXN123", txnData.getTransactionId());
        assertEquals("PURCHASE", txnData.getTransactionType());
        assertEquals(new BigDecimal("100.50"), txnData.getAmount());
        assertEquals("USD", txnData.getCurrency());
        assertEquals(25.5, txnData.getRiskScore());
        assertEquals("COMPLETED", txnData.getStatus());
    }
    
    @Test
    void testUnmarshalNestedCustomerData() throws Exception {
        String json = """
            {
                "transaction_id": "TXN456",
                "customer": {
                    "customer_id": "CUST001",
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+1-555-0123",
                    "address": {
                        "street": "123 Main St",
                        "city": "New York",
                        "state": "NY",
                        "postal_code": "10001",
                        "country": "USA"
                    },
                    "loyalty_tier": "GOLD"
                }
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData.getCustomer());
        assertEquals("CUST001", txnData.getCustomer().getCustomerId());
        assertEquals("John Doe", txnData.getCustomer().getName());
        assertEquals("john@example.com", txnData.getCustomer().getEmail());
        
        assertNotNull(txnData.getCustomer().getAddress());
        assertEquals("New York", txnData.getCustomer().getAddress().getCity());
        assertEquals("NY", txnData.getCustomer().getAddress().getState());
        assertEquals("USA", txnData.getCustomer().getAddress().getCountry());
    }
    
    @Test
    void testUnmarshalMerchantData() throws Exception {
        String json = """
            {
                "transaction_id": "TXN789",
                "merchant": {
                    "merchant_id": "MERCH123",
                    "name": "Coffee Shop",
                    "category": "Restaurant",
                    "mcc": "5812"
                }
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData.getMerchant());
        assertEquals("MERCH123", txnData.getMerchant().getMerchantId());
        assertEquals("Coffee Shop", txnData.getMerchant().getName());
        assertEquals("Restaurant", txnData.getMerchant().getCategory());
        assertEquals("5812", txnData.getMerchant().getMcc());
    }
    
    @Test
    void testUnmarshalPaymentMethod() throws Exception {
        String json = """
            {
                "transaction_id": "TXN999",
                "payment_method": {
                    "type": "CREDIT_CARD",
                    "last_four": "4532",
                    "brand": "VISA",
                    "expiry_month": 12,
                    "expiry_year": 2027
                }
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData.getPaymentMethod());
        assertEquals("CREDIT_CARD", txnData.getPaymentMethod().getType());
        assertEquals("4532", txnData.getPaymentMethod().getLastFour());
        assertEquals("VISA", txnData.getPaymentMethod().getBrand());
        assertEquals(12, txnData.getPaymentMethod().getExpiryMonth());
        assertEquals(2027, txnData.getPaymentMethod().getExpiryYear());
    }
    
    @Test
    void testUnmarshalLineItems() throws Exception {
        String json = """
            {
                "transaction_id": "TXN111",
                "items": [
                    {
                        "item_id": "ITEM001",
                        "name": "Laptop",
                        "quantity": 1,
                        "unit_price": 999.99,
                        "total_price": 999.99,
                        "category": "Electronics"
                    },
                    {
                        "item_id": "ITEM002",
                        "name": "Mouse",
                        "quantity": 2,
                        "unit_price": 25.00,
                        "total_price": 50.00,
                        "category": "Accessories"
                    }
                ]
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData.getItems());
        assertEquals(2, txnData.getItems().size());
        
        TransactionData.LineItem item1 = txnData.getItems().get(0);
        assertEquals("ITEM001", item1.getItemId());
        assertEquals("Laptop", item1.getName());
        assertEquals(1, item1.getQuantity());
        assertEquals(new BigDecimal("999.99"), item1.getUnitPrice());
        
        TransactionData.LineItem item2 = txnData.getItems().get(1);
        assertEquals("ITEM002", item2.getItemId());
        assertEquals(2, item2.getQuantity());
    }
    
    @Test
    void testUnmarshalMetadata() throws Exception {
        String json = """
            {
                "transaction_id": "TXN222",
                "metadata": {
                    "ip_address": "192.168.1.1",
                    "user_agent": "Mozilla/5.0",
                    "session_id": "sess_abc123",
                    "custom_field": "custom_value"
                }
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData.getMetadata());
        assertEquals("192.168.1.1", txnData.getMetadata().get("ip_address"));
        assertEquals("Mozilla/5.0", txnData.getMetadata().get("user_agent"));
        assertEquals("sess_abc123", txnData.getMetadata().get("session_id"));
        assertEquals("custom_value", txnData.getMetadata().get("custom_field"));
    }
    
    @Test
    void testUnmarshalCompleteTransaction() throws Exception {
        String json = """
            {
                "transaction_id": "TXN_COMPLETE",
                "transaction_type": "PURCHASE",
                "amount": 1299.99,
                "currency": "USD",
                "customer": {
                    "customer_id": "CUST_FULL",
                    "name": "Jane Smith",
                    "email": "jane@example.com",
                    "address": {
                        "city": "San Francisco",
                        "state": "CA",
                        "country": "USA"
                    }
                },
                "merchant": {
                    "merchant_id": "MERCH_FULL",
                    "name": "Tech Store"
                },
                "payment_method": {
                    "type": "CREDIT_CARD",
                    "brand": "MASTERCARD"
                },
                "items": [
                    {
                        "item_id": "ITEM_FULL",
                        "name": "Smartphone",
                        "quantity": 1,
                        "unit_price": 1299.99,
                        "total_price": 1299.99
                    }
                ],
                "risk_score": 15.5,
                "status": "COMPLETED"
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        // Verify all major components are present
        assertNotNull(txnData);
        assertEquals("TXN_COMPLETE", txnData.getTransactionId());
        assertNotNull(txnData.getCustomer());
        assertNotNull(txnData.getMerchant());
        assertNotNull(txnData.getPaymentMethod());
        assertNotNull(txnData.getItems());
        assertEquals(1, txnData.getItems().size());
        assertEquals(15.5, txnData.getRiskScore());
        assertEquals("COMPLETED", txnData.getStatus());
    }
    
    @Test
    void testHandleMissingOptionalFields() throws Exception {
        // Test that missing optional fields don't cause errors
        String json = """
            {
                "transaction_id": "TXN_MINIMAL"
            }
            """;
        
        TransactionData txnData = objectMapper.readValue(json, TransactionData.class);
        
        assertNotNull(txnData);
        assertEquals("TXN_MINIMAL", txnData.getTransactionId());
        assertNull(txnData.getCustomer());
        assertNull(txnData.getMerchant());
        assertNull(txnData.getPaymentMethod());
        assertNull(txnData.getItems());
    }
    
    @Test
    void testSerializeToJson() throws Exception {
        // Test that we can also serialize back to JSON
        TransactionData txnData = TransactionData.builder()
                .transactionId("TXN_SERIALIZE")
                .transactionType("PURCHASE")
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .status("PENDING")
                .build();
        
        String json = objectMapper.writeValueAsString(txnData);
        
        assertNotNull(json);
        assertTrue(json.contains("TXN_SERIALIZE"));
        assertTrue(json.contains("PURCHASE"));
        assertTrue(json.contains("500.00"));
    }
}
