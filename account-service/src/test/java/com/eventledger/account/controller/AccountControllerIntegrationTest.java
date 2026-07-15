package com.eventledger.account.controller;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.entity.Account;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void testGetBalanceForExistingAccount() throws Exception {
        // Arrange
        Account account = new Account("TEST_ACC");
        account.setBalance(new BigDecimal("500.00"));
        accountRepository.save(account);

        // Act & Assert
        mockMvc.perform(get("/accounts/TEST_ACC/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", equalTo("TEST_ACC")))
                .andExpect(jsonPath("$.balance", equalTo(500.0)));
    }

    @Test
    void testGetBalanceForNonExistentAccount() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/accounts/NONEXISTENT/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    void testApplyDebitTransaction() throws Exception {
        // Arrange
        Account account = new Account("ACC_001");
        account.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(
                "EVT_001",
                "DEBIT",
                new BigDecimal("250.00"),
                "USD",
                Instant.now()
        );

        // Act & Assert
        mockMvc.perform(post("/accounts/ACC_001/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", equalTo("ACC_001")))
                .andExpect(jsonPath("$.balance", equalTo(750.0)));
    }

    @Test
    void testApplyCreditTransaction() throws Exception {
        // Arrange
        Account account = new Account("ACC_002");
        account.setBalance(new BigDecimal("500.00"));
        accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(
                "EVT_002",
                "CREDIT",
                new BigDecimal("200.00"),
                "USD",
                Instant.now()
        );

        // Act & Assert
        mockMvc.perform(post("/accounts/ACC_002/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", equalTo("ACC_002")))
                .andExpect(jsonPath("$.balance", equalTo(700.0)));
    }

    @Test
    void testIdempotentTransactionProcessing() throws Exception {
        // Arrange
        Account account = new Account("ACC_003");
        account.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(
                "EVT_003",
                "DEBIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );

        // Act - First transaction
        mockMvc.perform(post("/accounts/ACC_003/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", equalTo(900.0)));

        // Act - Same transaction again (should be idempotent)
        mockMvc.perform(post("/accounts/ACC_003/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", equalTo(900.0))); // Balance unchanged
    }

    @Test
    void testInsufficientFunds() throws Exception {
        // Arrange
        Account account = new Account("ACC_004");
        account.setBalance(new BigDecimal("50.00"));
        accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(
                "EVT_004",
                "DEBIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );

        // Act & Assert
        mockMvc.perform(post("/accounts/ACC_004/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    void testInvalidTransactionType() throws Exception {
        // Arrange
        Account account = new Account("ACC_005");
        account.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(
                "EVT_005",
                "INVALID_TYPE",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );

        // Act & Assert
        mockMvc.perform(post("/accounts/ACC_005/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

