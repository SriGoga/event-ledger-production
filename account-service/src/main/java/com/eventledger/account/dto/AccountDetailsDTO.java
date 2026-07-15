package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountDetailsDTO(
    String accountId,
    BigDecimal balance,
    Instant createdAt,
    Instant updatedAt,
    List<TransactionDTO> recentTransactions
) {}

