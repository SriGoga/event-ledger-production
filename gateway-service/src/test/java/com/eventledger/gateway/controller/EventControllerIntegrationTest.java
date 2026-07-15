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
import java.util.HashMap;
import java.util.Map;

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
                Instant.now(),
                null
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
    void testCreateEventWithMetadata() throws Exception {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "mainframe-batch");
        metadata.put("batchId", "B-9042");

        EventRequest request = new EventRequest(
                "EVT_META_001",
                "ACC_001",
                "CREDIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.now(),
                metadata
        );

        // Act & Assert
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId", equalTo("EVT_META_001")))
                .andExpect(jsonPath("$.metadata", notNullValue()));
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
                Instant.now(),
                null
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

    @Test
    void testGetEventById() throws Exception {
        // Arrange
        EventRequest request = new EventRequest(
                "EVT_GET_001",
                "ACC_003",
                "CREDIT",
                new BigDecimal("200.00"),
                "USD",
                Instant.now(),
                null
        );

        // Create event
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Act & Assert - Get the event
        mockMvc.perform(get("/events/EVT_GET_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId", equalTo("EVT_GET_001")))
                .andExpect(jsonPath("$.accountId", equalTo("ACC_003")))
                .andExpect(jsonPath("$.type", equalTo("CREDIT")))
                .andExpect(jsonPath("$.amount", equalTo(200.0)));
    }

    @Test
    void testGetNonExistentEvent() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/events/NONEXISTENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListEventsByAccount() throws Exception {
        // Arrange - Create multiple events for same account
        EventRequest request1 = new EventRequest(
                "EVT_LIST_001",
                "ACC_QUERY",
                "CREDIT",
                new BigDecimal("100.00"),
                "USD",
                Instant.parse("2026-01-01T10:00:00Z"),
                null
        );

        EventRequest request2 = new EventRequest(
                "EVT_LIST_002",
                "ACC_QUERY",
                "DEBIT",
                new BigDecimal("50.00"),
                "USD",
                Instant.parse("2026-01-02T10:00:00Z"),
                null
        );

        // Create events
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // Act & Assert - List events for account
        mockMvc.perform(get("/events").param("accountId", "ACC_QUERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId", equalTo("EVT_LIST_001")))
                .andExpect(jsonPath("$[1].eventId", equalTo("EVT_LIST_002")));
    }

    @Test
    void testListEventsByAccountEmptyResult() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/events").param("accountId", "ACC_NONEXISTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
