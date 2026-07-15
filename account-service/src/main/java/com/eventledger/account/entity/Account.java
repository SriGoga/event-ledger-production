package com.eventledger.account.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
public class Account {
 @Id
 private String accountId;
 private BigDecimal balance = BigDecimal.ZERO;
}