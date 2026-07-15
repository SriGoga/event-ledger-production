package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.exception.EventProcessingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountClient {
    private static final Logger logger = LoggerFactory.getLogger(AccountClient.class);

    private final RestTemplate restTemplate;
    private final String accountServiceUrl;
    private final Tracer tracer;

    public AccountClient(
            RestTemplateBuilder builder,
            @Value("${account.service.url:http://localhost:8081}") String accountServiceUrl,
            Tracer tracer) {
        this.restTemplate = builder.build();
        this.accountServiceUrl = accountServiceUrl;
        this.tracer = tracer;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "fallback")
    public void apply(EventRequest request) {
        try (var span = tracer.nextSpan().name("account-client-apply").start()) {
            span.tag("event.id", request.eventId());
            span.tag("account.id", request.accountId());

            String url = accountServiceUrl + "/accounts/" + request.accountId() + "/transactions";
            logger.info("Calling account service at: {}", url);

            restTemplate.postForEntity(url, request, Void.class);
            logger.info("Successfully applied transaction for eventId: {}", request.eventId());
        } catch (RestClientException e) {
            logger.error("Error calling account service for eventId: {}", request.eventId(), e);
            throw new EventProcessingException("Failed to apply transaction to account service", e);
        }
    }

    public void fallback(EventRequest request, Exception ex) {
        logger.error("Circuit breaker fallback triggered for eventId: {}, exception: {}",
                    request.eventId(), ex.getMessage());
        throw new EventProcessingException("Account service unavailable - circuit breaker open", ex);
    }
}