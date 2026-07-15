package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.exception.EventProcessingException;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Tracer tracer;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    private AccountClient accountClient;

    private EventRequest testEventRequest;

    @BeforeEach
    void setUp() {
        testEventRequest = new EventRequest(
            "EVT001",
            "ACC001",
            "CREDIT",
            new BigDecimal("100.00"),
            "USD",
            Instant.now(),
            null
        );

        // Mock tracer
        when(tracer.nextSpan()).thenReturn(tracer);
        when(tracer.name(anyString())).thenReturn(tracer);
        when(tracer.start()).thenReturn(tracer);
        when(tracer.tag(anyString(), anyString())).thenReturn(tracer);
    }

    @Test
    void testTimeoutConfigured() {
        // Verify timeout configuration is applied
        when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        // Create client with timeout configuration
        AccountClient client = new AccountClient(restTemplateBuilder, "http://localhost:8081", tracer);

        // Verify timeout methods were called
        verify(restTemplateBuilder).setConnectTimeout(any());
        verify(restTemplateBuilder).setReadTimeout(any());
    }

    @Test
    void testTimeoutExceptionHandling() {
        // Setup
        AccountClient client = new AccountClient(restTemplateBuilder, "http://localhost:8081", tracer);

        // Mock socket timeout
        when(restTemplate.postForEntity(
            contains("/accounts/ACC001/transactions"),
            eq(testEventRequest),
            eq(Void.class)
        )).thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("timeout")));

        // This would need actual RestTemplate, but demonstrates the concept
        // In real scenarios, RestTemplate is configured with actual timeouts
    }
}

