package com.eventledger.gateway.client;
import com.eventledger.gateway.dto.EventRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
@Component
public class AccountClient {
 private final RestTemplate restTemplate=new RestTemplate();
 @CircuitBreaker(name="accountService", fallbackMethod="fallback")
 public void apply(EventRequest request){
   restTemplate.postForEntity(
     "http://localhost:8081/accounts/"+request.accountId()+"/transactions",
     request,Void.class);
 }
 public void fallback(EventRequest request, Exception ex){
   throw new RuntimeException("Account Service unavailable");
 }
}