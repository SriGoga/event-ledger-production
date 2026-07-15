package com.eventledger.account.repository;
import com.eventledger.account.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction,Long>{
    boolean existsByEventId(String eventId);
    List<Transaction> findByAccountIdOrderByCreatedAtDesc(String accountId);
}