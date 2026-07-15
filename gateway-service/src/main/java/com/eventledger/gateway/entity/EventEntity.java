package com.eventledger.gateway.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_account_id", columnList = "accountId"),
    @Index(name = "idx_event_id", columnList = "eventId")
})
public class EventEntity {
    @Id
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private Instant eventTimestamp;

    private String metadata;

    private Instant receivedAt;

    private Boolean processed = false;

    public enum EventType {
        DEBIT, CREDIT
    }

    public EventEntity() {}

    public EventEntity(String eventId, String accountId, EventType type, BigDecimal amount, String currency) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.receivedAt = Instant.now();
        this.processed = false;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Boolean getProcessed() {
        return processed;
    }

    public void setProcessed(Boolean processed) {
        this.processed = processed;
    }
}