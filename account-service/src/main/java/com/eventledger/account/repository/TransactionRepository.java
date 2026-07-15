package com.eventledger.account.repository;
import com.eventledger.account.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TransactionRepository extends JpaRepository<Transaction,Long>{
 boolean existsByEventId(String eventId);
}