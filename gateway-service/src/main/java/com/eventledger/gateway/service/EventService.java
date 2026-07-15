package com.eventledger.gateway.service;
import com.eventledger.gateway.client.AccountClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.repository.EventRepository;
import org.springframework.stereotype.Service;
@Service
public class EventService {
 private final EventRepository repo;
 private final AccountClient client;
 public EventService(EventRepository repo, AccountClient client){
   this.repo=repo; this.client=client;
 }
 public EventEntity create(EventRequest request){
   return repo.findById(request.eventId())
      .orElseGet(() -> {
         EventEntity e=new EventEntity();
         client.apply(request);
         return repo.save(e);
      });
 }
}