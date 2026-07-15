package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountClient;
import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.entity.EventEntity;
import com.eventledger.gateway.exception.EventProcessingException;
import com.eventledger.gateway.repository.EventRepository;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private EventService eventService;

    private EventRequest testEventRequest;

    @BeforeEach
    void setUp() {
        testEventRequest = new EventRequest(
                "EVT001",
                "ACC001",
                "CREDIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );

        // Mock tracer to return a closeable span
        when(tracer.nextSpan()).thenReturn(tracer);
        when(tracer.name(anyString())).thenReturn(tracer);
        when(tracer.start()).thenReturn(tracer);
        when(tracer.tag(anyString(), anyString())).thenReturn(tracer);
    }

    @Test
    void testCreateEventIdempotency() {
        // Arrange - event already exists
        EventEntity existingEvent = new EventEntity(
                "EVT001", "ACC001", EventEntity.EventType.CREDIT,
                new BigDecimal("100.00"), "USD"
        );
        when(eventRepository.findById("EVT001")).thenReturn(Optional.of(existingEvent));

        // Act
        EventEntity result = eventService.create(testEventRequest);

        // Assert
        assertNotNull(result);
        assertEquals("EVT001", result.getEventId());
        verify(accountClient, never()).apply(any()); // Should not call account service
    }

    @Test
    void testCreateNewEvent() {
        // Arrange
        when(eventRepository.findById("EVT001")).thenReturn(Optional.empty());

        EventEntity newEvent = new EventEntity(
                "EVT001", "ACC001", EventEntity.EventType.CREDIT,
                new BigDecimal("100.00"), "USD"
        );
        when(eventRepository.save(any(EventEntity.class))).thenReturn(newEvent);
        doNothing().when(accountClient).apply(testEventRequest);

        // Act
        EventEntity result = eventService.create(testEventRequest);

        // Assert
        assertNotNull(result);
        assertEquals("EVT001", result.getEventId());
        assertEquals("ACC001", result.getAccountId());
        assertEquals(EventEntity.EventType.CREDIT, result.getType());
        verify(accountClient, times(1)).apply(testEventRequest);
        verify(eventRepository, times(2)).save(any(EventEntity.class));
    }

    @Test
    void testCreateEventWithAccountServiceFailure() {
        // Arrange
        when(eventRepository.findById("EVT001")).thenReturn(Optional.empty());

        EventEntity newEvent = new EventEntity(
                "EVT001", "ACC001", EventEntity.EventType.CREDIT,
                new BigDecimal("100.00"), "USD"
        );
        when(eventRepository.save(any(EventEntity.class))).thenReturn(newEvent);
        doThrow(new EventProcessingException("Account service failed"))
                .when(accountClient).apply(testEventRequest);

        // Act & Assert
        assertThrows(EventProcessingException.class, () ->
                eventService.create(testEventRequest)
        );
        verify(accountClient, times(1)).apply(testEventRequest);
    }

    @Test
    void testCreateEventWithInvalidType() {
        // Arrange
        EventRequest invalidRequest = new EventRequest(
                "EVT002",
                "ACC001",
                "INVALID",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );
        when(eventRepository.findById("EVT002")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EventProcessingException.class, () ->
                eventService.create(invalidRequest)
        );
    }
}

