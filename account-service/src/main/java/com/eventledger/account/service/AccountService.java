package com.eventledger.account.service;

import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.Transaction;
import com.eventledger.account.exception.AccountException;
import com.eventledger.account.exception.IdempotencyException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class AccountService {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountResponse applyTransaction(String accountId, TransactionRequest request) {
        logger.info("Applying transaction for account: {}, eventId: {}", accountId, request.eventId());

        // Idempotency check: prevent duplicate processing
        if (transactionRepository.existsByEventId(request.eventId())) {
            logger.warn("Transaction already exists for eventId: {}", request.eventId());
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountException("Account not found: " + accountId));
            return mapToResponse(account);
        }

        // Get or create account
        Account account = accountRepository.findById(accountId)
                .orElseGet(() -> {
                    logger.info("Creating new account: {}", accountId);
                    Account newAccount = new Account(accountId);
                    return accountRepository.save(newAccount);
                });

        // Parse transaction type
        Transaction.TransactionType txType;
        try {
            txType = Transaction.TransactionType.valueOf(request.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AccountException("Invalid transaction type: " + request.type());
        }

        // Apply transaction
        BigDecimal newBalance = applyTransactionLogic(account.getBalance(), txType, request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new AccountException("Insufficient funds for account: " + accountId);
        }

        account.setBalance(newBalance);
        Account updatedAccount = accountRepository.save(account);
        logger.info("Account balance updated to {} for account {}", newBalance, accountId);

        // Record transaction
        Transaction transaction = new Transaction(
                request.eventId(),
                accountId,
                txType,
                request.amount(),
                request.currency()
        );
        transaction.setTransactionTime(request.eventTimestamp() != null ? request.eventTimestamp() : Instant.now());
        transactionRepository.save(transaction);
        logger.info("Transaction recorded for eventId: {}", request.eventId());

        return mapToResponse(updatedAccount);
    }

    public AccountResponse getBalance(String accountId) {
        logger.info("Fetching balance for account: {}", accountId);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountException("Account not found: " + accountId));
        return mapToResponse(account);
    }

    private BigDecimal applyTransactionLogic(BigDecimal currentBalance, Transaction.TransactionType type, BigDecimal amount) {
        return switch (type) {
            case CREDIT -> currentBalance.add(amount);
            case DEBIT -> currentBalance.subtract(amount);
        };
    }

    private AccountResponse mapToResponse(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}