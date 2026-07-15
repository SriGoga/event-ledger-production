package com.eventledger.gateway.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="events")
public class EventEntity {
@Id
private String eventId;
private String accountId;
private String type;
private BigDecimal amount;
private String currency;
private Instant eventTimestamp;
private String metadata;
}