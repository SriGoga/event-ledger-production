package com.eventledger.account.service;

import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.Transaction;
import com.eventledger.account.exception.AccountException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private TransactionRequest transactionRequest;

    @BeforeEach
    void setUp() {
        testAccount = new Account("ACC001");
        testAccount.setBalance(new BigDecimal("100.00"));

        transactionRequest = new TransactionRequest(
                "EVT001",
                "DEBIT",
                new BigDecimal("50.00"),
                "USD",
                Instant.now()
        );
    }

    @Test
    void testApplyTransactionCredit() {
        // Arrange
        when(transactionRepository.existsByEventId("EVT002")).thenReturn(false);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        TransactionRequest creditRequest = new TransactionRequest(
                "EVT002",
                "CREDIT",
                new BigDecimal("25.00"),
                "USD",
                Instant.now()
        );

        // Act
        AccountResponse response = accountService.applyTransaction("ACC001", creditRequest);

        // Assert
        assertNotNull(response);
        assertEquals("ACC001", response.accountId());
        verify(accountRepository, times(1)).save(any(Account.class));
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void testApplyTransactionDebit() {
        // Arrange
        when(transactionRepository.existsByEventId("EVT001")).thenReturn(false);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        // Act
        AccountResponse response = accountService.applyTransaction("ACC001", transactionRequest);

        // Assert
        assertNotNull(response);
        assertEquals("ACC001", response.accountId());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testApplyTransactionIdempotency() {
        // Arrange - event already exists
        when(transactionRepository.existsByEventId("EVT001")).thenReturn(true);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));

        // Act
        AccountResponse response = accountService.applyTransaction("ACC001", transactionRequest);

        // Assert
        assertNotNull(response);
        verify(accountRepository, times(1)).findById("ACC001");
        verify(accountRepository, times(0)).save(any(Account.class)); // Should not save again
    }

    @Test
    void testApplyTransactionInsufficientFunds() {
        // Arrange
        testAccount.setBalance(new BigDecimal("10.00")); // Less than 50
        when(transactionRepository.existsByEventId("EVT001")).thenReturn(false);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));

        // Act & Assert
        assertThrows(AccountException.class, () ->
                accountService.applyTransaction("ACC001", transactionRequest)
        );
    }

    @Test
    void testGetBalance() {
        // Arrange
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));

        // Act
        AccountResponse response = accountService.getBalance("ACC001");

        // Assert
        assertNotNull(response);
        assertEquals("ACC001", response.accountId());
        assertEquals(new BigDecimal("100.00"), response.balance());
    }

    @Test
    void testGetBalanceAccountNotFound() {
        // Arrange
        when(accountRepository.findById("INVALID")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountException.class, () ->
                accountService.getBalance("INVALID")
        );
    }

    @Test
    void testApplyTransactionCreateNewAccount() {
        // Arrange
        when(transactionRepository.existsByEventId("EVT001")).thenReturn(false);
        when(accountRepository.findById("NEW_ACC")).thenReturn(Optional.empty());

        Account newAccount = new Account("NEW_ACC");
        when(accountRepository.save(any(Account.class))).thenReturn(newAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        TransactionRequest creditRequest = new TransactionRequest(
                "EVT003",
                "CREDIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );

        // Act
        AccountResponse response = accountService.applyTransaction("NEW_ACC", creditRequest);

        // Assert
        assertNotNull(response);
        assertEquals("NEW_ACC", response.accountId());
        verify(accountRepository, times(1)).findById("NEW_ACC");
    }

    @Test
    void testApplyTransactionWithInvalidType() {
        // Arrange
        when(transactionRepository.existsByEventId("EVT004")).thenReturn(false);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));

        TransactionRequest invalidRequest = new TransactionRequest(
                "EVT004",
                "INVALID_TYPE",
                new BigDecimal("50.00"),
                "USD",
                Instant.now()
        );

        // Act & Assert
        assertThrows(AccountException.class, () ->
                accountService.applyTransaction("ACC001", invalidRequest)
        );
    }

    @Test
    void testApplyTransactionVerifySavedTransactionFields() {
        // Arrange
        Instant eventTimestamp = Instant.parse("2026-07-15T10:30:00Z");
        when(transactionRepository.existsByEventId("EVT005")).thenReturn(false);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        TransactionRequest request = new TransactionRequest(
                "EVT005",
                "DEBIT",
                new BigDecimal("30.00"),
                "EUR",
                eventTimestamp
        );

        // Act
        accountService.applyTransaction("ACC001", request);

        // Assert - capture and verify saved Transaction
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        Transaction savedTx = captor.getValue();
        assertEquals("EVT005", savedTx.getEventId());
        assertEquals("ACC001", savedTx.getAccountId());
        assertEquals(Transaction.TransactionType.DEBIT, savedTx.getType());
        assertEquals(new BigDecimal("30.00"), savedTx.getAmount());
        assertEquals("EUR", savedTx.getCurrency());
        assertEquals(eventTimestamp, savedTx.getTransactionTime());
    }

    @Test
    void testApplyTransactionWithoutEventTimestampUsesNow() {
        // Arrange
        when(transactionRepository.existsByEventId("EVT006")).thenReturn(false);
        when(accountRepository.findById("ACC001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(new Transaction());

        TransactionRequest request = new TransactionRequest(
                "EVT006",
                "CREDIT",
                new BigDecimal("50.00"),
                "GBP",
                null  // No timestamp provided
        );

        Instant beforeCall = Instant.now();
        // Act
        accountService.applyTransaction("ACC001", request);
        Instant afterCall = Instant.now();

        // Assert - capture and verify transaction time is set to approximately now
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).save(captor.capture());

        Transaction savedTx = captor.getValue();
        assertNotNull(savedTx.getTransactionTime());
        assertFalse(savedTx.getTransactionTime().isBefore(beforeCall));
        assertFalse(savedTx.getTransactionTime().isAfter(afterCall));
    }
}

