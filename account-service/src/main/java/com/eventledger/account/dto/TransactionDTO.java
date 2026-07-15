package com.eventledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDTO(
    String eventId,
    String type,
    BigDecimal amount,
    String currency,
    Instant transactionTime,
    Instant createdAt
) {}

