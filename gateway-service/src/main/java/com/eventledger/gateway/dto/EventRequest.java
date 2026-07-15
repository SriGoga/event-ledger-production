package com.eventledger.gateway.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EventRequest(
    @NotBlank String eventId,
    @NotBlank String accountId,
    @NotBlank String type,
    @Positive BigDecimal amount,
    @NotBlank String currency,
    Instant eventTimestamp,
    @JsonProperty(required = false)
    Map<String, Object> metadata
) {}
