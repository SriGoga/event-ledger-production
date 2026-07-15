package com.eventledger.gateway.repository;
import com.eventledger.gateway.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EventRepository extends JpaRepository<EventEntity,String>{
 List<EventEntity> findByAccountIdOrderByEventTimestamp(String accountId);
}