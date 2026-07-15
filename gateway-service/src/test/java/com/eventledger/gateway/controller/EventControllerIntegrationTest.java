package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.EventRequest;
import com.eventledger.gateway.repository.EventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void testCreateValidEvent() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
                "EVT_001",
                "ACC_001",
                "CREDIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.now()
        );

        // Act & Assert
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", equalTo("EVT_001")))
                .andExpect(jsonPath("$.accountId", equalTo("ACC_001")))
                .andExpect(jsonPath("$.type", equalTo("CREDIT")));
    }

    @Test
    void testCreateEventWithMissingEventId() throws Exception {
        // Arrange - missing eventId
        String requestBody = "{\"accountId\": \"ACC_001\", \"type\": \"CREDIT\", \"amount\": 100, \"currency\": \"USD\"}";

        // Act & Assert
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateEventWithNegativeAmount() throws Exception {
        // Arrange
        String requestBody = "{\"eventId\": \"EVT_002\", \"accountId\": \"ACC_001\", \"type\": \"CREDIT\", \"amount\": -100, \"currency\": \"USD\"}";

        // Act & Assert
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEventIdempotency() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
                "EVT_003",
                "ACC_002",
                "DEBIT",
                new BigDecimal("50.00"),
                "USD",
                Instant.now()
        );

        // Act - First request
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", equalTo("EVT_003")));

        // Act - Duplicate request
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", equalTo("EVT_003")));

        // Assert - Only one event in database
        assert eventRepository.findById("EVT_003").isPresent();
    }
}

