package com.eventledger.gateway.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
public record EventRequest(
@NotBlank String eventId,
@NotBlank String accountId,
@NotBlank String type,
@Positive BigDecimal amount,
@NotBlank String currency,
Instant eventTimestamp){}