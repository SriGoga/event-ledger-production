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
    void testGetAccountDetailsWithTransactions() throws Exception {
        // Arrange
        Account account = new Account("ACC_DETAILS");
        account.setBalance(new BigDecimal("1000.00"));
        accountRepository.save(account);

        // Create some transactions
        TransactionRequest request1 = new TransactionRequest(
                "EVT_D_001",
                "CREDIT",
                new BigDecimal("500.00"),
                "USD",
                Instant.now()
        );

        mockMvc.perform(post("/accounts/ACC_DETAILS/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // Act & Assert - Get account details
        mockMvc.perform(get("/accounts/ACC_DETAILS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", equalTo("ACC_DETAILS")))
                .andExpect(jsonPath("$.balance", equalTo(1500.0)))
                .andExpect(jsonPath("$.recentTransactions", hasSize(1)))
                .andExpect(jsonPath("$.recentTransactions[0].eventId", equalTo("EVT_D_001")));
    }

    @Test
    void testGetAccountDetailsNonExistent() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/accounts/NONEXISTENT"))
                .andExpect(status().isBadRequest());
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

    @Test
    void testOutOfOrderEventProcessing() throws Exception {
        // Arrange - Create 3 transactions with timestamps in different order
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T10:00:00Z");
        Instant t3 = Instant.parse("2026-01-03T10:00:00Z");

        // Arrange
        Account account = new Account("ACC_OUT");
        account.setBalance(BigDecimal.ZERO);
        accountRepository.save(account);

        // Apply t3, then t1, then t2 (out of order)

        // Transaction 1: Credit 300 at t3
        mockMvc.perform(post("/accounts/ACC_OUT/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(
                        "EVT_OUT_3", "CREDIT", new BigDecimal("300"), "USD", t3))))
                .andExpect(status().isOk());

        // Transaction 2: Credit 100 at t1
        mockMvc.perform(post("/accounts/ACC_OUT/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(
                        "EVT_OUT_1", "CREDIT", new BigDecimal("100"), "USD", t1))))
                .andExpect(status().isOk());

        // Transaction 3: Credit 200 at t2
        mockMvc.perform(post("/accounts/ACC_OUT/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransactionRequest(
                        "EVT_OUT_2", "CREDIT", new BigDecimal("200"), "USD", t2))))
                .andExpect(status().isOk());

        // Verify final balance is correct: 100 + 200 + 300 = 600
        mockMvc.perform(get("/accounts/ACC_OUT/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", equalTo(600.0)));
    }
}

