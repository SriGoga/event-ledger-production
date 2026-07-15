package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.exception.EventProcessingException;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    private final EventRepository repo;
    private final AccountClient client;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    public EventService(EventRepository repo, AccountClient client, Tracer tracer, ObjectMapper objectMapper) {
        this.repo = repo;
        this.client = client;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventEntity create(EventRequest request) {
        try (var span = tracer.nextSpan().name("event-service-create").start()) {
            span.tag("event.id", request.eventId());
            span.tag("account.id", request.accountId());

            logger.info("Processing event: {}", request.eventId());

            // Idempotency: check if event already exists
            return repo.findById(request.eventId())
                    .map(existing -> {
                        logger.info("Event already exists: {}, returning cached result", request.eventId());
                        return existing;
                    })
                    .orElseGet(() -> {
                        logger.info("Creating new event: {}", request.eventId());

                        // Create event entity with all fields from request
                        EventEntity.EventType eventType;
                        try {
                            eventType = EventEntity.EventType.valueOf(request.type().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new EventProcessingException("Invalid event type: " + request.type());
                        }

                        EventEntity event = new EventEntity(
                                request.eventId(),
                                request.accountId(),
                                eventType,
                                request.amount(),
                                request.currency()
                        );

                        if (request.eventTimestamp() != null) {
                            event.setEventTimestamp(request.eventTimestamp());
                        }

                        // Serialize metadata if provided
                        if (request.metadata() != null) {
                            try {
                                event.setMetadata(objectMapper.writeValueAsString(request.metadata()));
                            } catch (Exception e) {
                                logger.warn("Failed to serialize metadata for event: {}", request.eventId(), e);
                            }
                        }

                        // Save event first (idempotency key)
                        EventEntity savedEvent = repo.save(event);
                        logger.info("Event saved: {}", request.eventId());

                        // Apply transaction to account service with circuit breaker
                        try {
                            client.apply(request);
                            savedEvent.setProcessed(true);
                            logger.info("Event processed successfully: {}", request.eventId());
                        } catch (Exception e) {
                            logger.error("Failed to process event: {}, error: {}", request.eventId(), e.getMessage());
                            savedEvent.setProcessed(false);
                            throw e;
                        }

                        repo.save(savedEvent);
                        return savedEvent;
                    });
        }
    }
}