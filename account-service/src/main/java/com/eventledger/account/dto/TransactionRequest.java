package com.eventledger.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
    @NotBlank String eventId,
    @NotBlank String type,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String currency,
    Instant eventTimestamp
) {}

