package com.eventledger.account.controller;

import com.eventledger.account.dto.AccountDetailsDTO;
import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.TransactionDTO;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.Transaction;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    public AccountController(AccountService accountService, TransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountResponse> balance(@PathVariable String accountId) {
        AccountResponse response = accountService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailsDTO> getAccount(@PathVariable String accountId) {
        AccountResponse accountResponse = accountService.getBalance(accountId);

        // Fetch recent transactions (last 10)
        List<Transaction> transactions = transactionRepository
            .findByAccountIdOrderByCreatedAtDesc(accountId)
            .stream()
            .limit(10)
            .collect(Collectors.toList());

        List<TransactionDTO> transactionDTOs = transactions.stream()
            .map(tx -> new TransactionDTO(
                tx.getEventId(),
                tx.getType().name(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getTransactionTime(),
                tx.getCreatedAt()
            ))
            .collect(Collectors.toList());

        AccountDetailsDTO details = new AccountDetailsDTO(
            accountResponse.accountId(),
            accountResponse.balance(),
            accountResponse.createdAt(),
            accountResponse.updatedAt(),
            transactionDTOs
        );

        return ResponseEntity.ok(details);
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<AccountResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request) {
        AccountResponse response = accountService.applyTransaction(accountId, request);
        return ResponseEntity.ok(response);
    }
}

