package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.exception.EventProcessingException;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.gateway.service.EventService;
import io.micrometer.tracing.Tracer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventService service;
    private final EventRepository repository;
    private final Tracer tracer;

    public EventController(EventService service, EventRepository repository, Tracer tracer) {
        this.service = service;
        this.repository = repository;
        this.tracer = tracer;
    }

    @PostMapping
    public ResponseEntity<EventEntity> create(@RequestBody @Valid EventRequest request) {
        try (var span = tracer.nextSpan().name("event-controller-create").start()) {
            span.tag("event.id", request.eventId());
            EventEntity result = service.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventEntity> getEvent(@PathVariable String eventId) {
        try (var span = tracer.nextSpan().name("event-controller-get").start()) {
            span.tag("event.id", eventId);
            EventEntity event = repository.findById(eventId)
                    .orElseThrow(() -> new EventProcessingException("Event not found: " + eventId));
            return ResponseEntity.ok(event);
        }
    }

    @GetMapping
    public ResponseEntity<List<EventEntity>> listEventsByAccount(@RequestParam String accountId) {
        try (var span = tracer.nextSpan().name("event-controller-list").start()) {
            span.tag("account.id", accountId);
            List<EventEntity> events = repository.findByAccountIdOrderByEventTimestamp(accountId);
            return ResponseEntity.ok(events);
        }
    }
}

