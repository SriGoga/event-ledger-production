package com.eventledger.account.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
public class Transaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;
 @Column(unique=true)
 private String eventId;
 private String accountId;
 private String type;
 private BigDecimal amount;
}