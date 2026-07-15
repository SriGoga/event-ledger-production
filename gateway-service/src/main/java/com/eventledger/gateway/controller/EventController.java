package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.service.EventService;
import io.micrometer.tracing.Tracer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventService service;
    private final Tracer tracer;

    public EventController(EventService service, Tracer tracer) {
        this.service = service;
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
}