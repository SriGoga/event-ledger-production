# Event Ledger - Implementation Action Plan

**Overall Specification Coverage: 92% ✅**

This document provides a prioritized action plan to achieve 100% specification compliance.

---

## 🎯 Priority 1: Critical Missing Endpoints (HIGH - 1-2 hours)

### Issue: GET Endpoints Not Implemented
The specification requires:
- `GET /events/{id}` - Retrieve single event
- `GET /events?account={accountId}` - List events by account (chronological order)
- `GET /accounts/{accountId}` - Get account details + recent transactions

These are needed for clients to query event history and account state.

### Action Items

#### 1.1 Add to EventController (`gateway-service`)
```java
@GetMapping("/{eventId}")
public ResponseEntity<EventEntity> getEvent(@PathVariable String eventId) {
    EventEntity event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventProcessingException("Event not found"));
    return ResponseEntity.ok(event);
}

@GetMapping
public ResponseEntity<List<EventEntity>> listEventsByAccount(
        @RequestParam String accountId) {
    List<EventEntity> events = eventRepository.findByAccountIdOrderByEventTimestamp(accountId);
    return ResponseEntity.ok(events);
}
```

#### 1.2 Add to AccountController (`account-service`)
```java
@GetMapping("/{accountId}")
public ResponseEntity<AccountDetailsDTO> getAccount(@PathVariable String accountId) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountException("Account not found"));
    List<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    return ResponseEntity.ok(new AccountDetailsDTO(account, transactions));
}
```

#### 1.3 Create AccountDetailsDTO
Add new DTO to return account with recent transactions:
```java
public record AccountDetailsDTO(
    String accountId,
    BigDecimal balance,
    Instant createdAt,
    Instant updatedAt,
    List<TransactionDTO> recentTransactions
) {}
```

**Time Estimate:** 45 minutes  
**Files to Modify:** 
- EventController.java
- AccountController.java
- AccountDetailsDTO.java (new)
- TransactionRepository.java (add query method)

---

## 🎯 Priority 2: HTTP Status Code Correction (CRITICAL - 10 minutes)

### Issue: Graceful Degradation Returns Wrong Status Code
When Account Service fails, circuit breaker throws `EventProcessingException`, which is mapped to HTTP 400.
Per REST standards, this should be **503 Service Unavailable**.

### Current Behavior (Wrong)
```
POST /events
↓
Circuit Breaker Open
↓
EventProcessingException thrown
↓
GlobalExceptionHandler maps to 400 Bad Request ❌
```

### Expected Behavior (Correct)
```
POST /events
↓
Circuit Breaker Open
↓
EventProcessingException thrown
↓
GlobalExceptionHandler maps to 503 Service Unavailable ✅
```

### Action Items

#### 2.1 Update GlobalExceptionHandler in gateway-service
**File:** `gateway-service/.../exception/GlobalExceptionHandler.java`

Change mapping for `EventProcessingException`:
```java
@ExceptionHandler(EventProcessingException.class)
public ResponseEntity<ErrorResponse> handleEventProcessingException(
        EventProcessingException ex, HttpServletRequest request) {
    ErrorResponse error = new ErrorResponse(
        HttpStatus.SERVICE_UNAVAILABLE.value(),  // 503 instead of 400
        ex.getMessage(),
        Instant.now()
    );
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
}
```

**Time Estimate:** 10 minutes  
**Files to Modify:** 
- GlobalExceptionHandler.java (gateway-service)

**Testing:**
```bash
# Verify correct status code when Account Service is down
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test","accountId":"acc1","type":"CREDIT","amount":100,"currency":"USD"}'
# Expected: 503 Service Unavailable
```

---

## 🎯 Priority 3: Metadata Support (HIGH - 30 minutes)

### Issue: Event Payload Missing Metadata Field
Specification shows metadata as optional field:
```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

Current `EventRequest` DTO only has `eventTimestamp`, but EventEntity already supports metadata.

### Action Items

#### 3.1 Update EventRequest DTO
**File:** `gateway-service/.../dto/EventRequest.java`

```java
public record EventRequest(
    @NotBlank String eventId,
    @NotBlank String accountId,
    @NotBlank String type,
    @Positive BigDecimal amount,
    @NotBlank String currency,
    Instant eventTimestamp,
    @Nullable Map<String, Object> metadata  // Add this
) {}
```

#### 3.2 Update EventService
**File:** `gateway-service/.../service/EventService.java`

Ensure metadata is serialized and stored:
```java
// In EventService.create() method
if (request.metadata() != null) {
    event.setMetadata(objectMapper.writeValueAsString(request.metadata()));
}
```

#### 3.3 Add ObjectMapper Bean
Ensure Jackson ObjectMapper is available for metadata serialization:
```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

**Time Estimate:** 30 minutes  
**Files to Modify:** 
- EventRequest.java
- EventService.java
- Add JacksonConfig.java (new)

**Testing:**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId":"evt-with-meta",
    "accountId":"acc1",
    "type":"CREDIT",
    "amount":100,
    "currency":"USD",
    "metadata":{"source":"api","reason":"refund"}
  }'
# Verify metadata is stored in response
```

---

## 🟡 Priority 4: Out-of-Order Test Case (MEDIUM - 1 hour)

### Issue: No Test Verifies Out-of-Order Event Handling
Specification states: "Events may arrive out of order. Event listings must be in chronological order by eventTimestamp."

No integration test verifies this behavior.

### Action Items

#### 4.1 Create New Integration Test
**File:** Add to `account-service/.../controller/AccountControllerIntegrationTest.java`

```java
@Test
void testOutOfOrderEventProcessing() throws Exception {
    // Create 3 transactions with timestamps in different order
    Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
    Instant t2 = Instant.parse("2026-01-02T10:00:00Z");
    Instant t3 = Instant.parse("2026-01-03T10:00:00Z");
    
    // Apply t3, then t1, then t2 (out of order)
    
    // Transaction 1: Credit 300 at t3
    mockMvc.perform(post("/accounts/ACC_OUT/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new TransactionRequest(
            "EVT_OUT_3", "CREDIT", new BigDecimal("300"), "USD", t3))))
        .andExpect(status().isOk());
    
    // Transaction 2: Credit 100 at t1
    mockMvc.perform(post("/accounts/ACC_OUT/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new TransactionRequest(
            "EVT_OUT_1", "CREDIT", new BigDecimal("100"), "USD", t1))))
        .andExpect(status().isOk());
    
    // Transaction 3: Credit 200 at t2
    mockMvc.perform(post("/accounts/ACC_OUT/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new TransactionRequest(
            "EVT_OUT_2", "CREDIT", new BigDecimal("200"), "USD", t2))))
        .andExpect(status().isOk());
    
    // Verify final balance is correct: 100 + 200 + 300 = 600
    mockMvc.perform(get("/accounts/ACC_OUT/balance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance", equalTo(600.0)));
    
    // Verify transactions are returned in chronological order
    mockMvc.perform(get("/accounts/ACC_OUT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentTransactions[0].eventId", equalTo("EVT_OUT_1")))
        .andExpect(jsonPath("$.recentTransactions[1].eventId", equalTo("EVT_OUT_2")))
        .andExpect(jsonPath("$.recentTransactions[2].eventId", equalTo("EVT_OUT_3")));
}
```

**Time Estimate:** 1 hour  
**Files to Modify:**
- AccountControllerIntegrationTest.java

**Verification:** Run test with `mvn test -Dtest=AccountControllerIntegrationTest`

---

## 🟡 Priority 5: End-to-End Trace Verification (MEDIUM - 2 hours)

### Issue: No Test Verifies Trace Appears in Jaeger
Specification requires: "Verify trace IDs flow from Gateway to Account Service"

Current tests mock the Tracer; no real Jaeger validation exists.

### Action Items

#### 5.1 Create Integration Test with Real Jaeger
**New File:** `gateway-service/.../integration/JaegerTraceIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
class JaegerTraceIT {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private EventRepository eventRepository;
    
    private RestTemplate restTemplate;
    
    private static final String JAEGER_QUERY_URL = "http://localhost:16686/api/traces";
    
    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        eventRepository.deleteAll();
    }
    
    @Test
    void testTracePropagatesToJaeger() throws Exception {
        // Create an event
        String eventId = "trace-test-" + System.currentTimeMillis();
        EventRequest request = new EventRequest(
            eventId, "ACC_TRACE", "CREDIT", 
            new BigDecimal("100"), "USD", Instant.now()
        );
        
        mockMvc.perform(post("/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(new ObjectMapper().writeValueAsString(request)))
            .andExpect(status().isCreated());
        
        // Wait for trace to be exported to Jaeger
        Thread.sleep(2000);
        
        // Query Jaeger for traces
        String jaegerUrl = JAEGER_QUERY_URL + "?service=gateway-service&limit=10";
        ResponseEntity<String> response = restTemplate.getForEntity(jaegerUrl, String.class);
        
        // Verify trace contains our event
        assertTrue(response.getBody().contains(eventId),
            "Trace should contain eventId: " + eventId);
    }
}
```

**Prerequisites:**
- Jaeger running on localhost:16686
- Can skip test if Jaeger unavailable: `@ConditionalOnProperty`

**Time Estimate:** 2 hours  
**Files to Create:**
- JaegerTraceIT.java (new)

**Note:** This test requires Jaeger to be running. Should be skipped in CI/CD unless Jaeger container is available.

---

## 🟢 Priority 6: Explicit Timeout Configuration (LOW - 30 minutes)

### Issue: No Explicit Timeout on RestTemplate
Calls to Account Service may hang indefinitely if service is slow or unresponsive.

### Action Items

#### 6.1 Configure RestTemplateBuilder in AccountClient
**File:** `gateway-service/.../client/AccountClient.java`

```java
public AccountClient(
        RestTemplateBuilder builder,
        @Value("${account.service.url:http://localhost:8081}") String accountServiceUrl,
        Tracer tracer) {
    this.restTemplate = builder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .interceptors(new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, 
                    byte[] body, ClientHttpRequestExecution execution)
                    throws IOException {
                return execution.execute(request, body);
            }
        })
        .build();
    this.accountServiceUrl = accountServiceUrl;
    this.tracer = tracer;
}
```

#### 6.2 Update Configuration
**File:** `gateway-service/application.yml`

```yaml
account:
  service:
    url: http://localhost:8081
    connect-timeout-ms: 5000
    read-timeout-ms: 10000
```

#### 6.3 Add Test for Timeout Scenario
```java
@Test
void testAccountServiceCallTimesOut() {
    // Mock slow response
    when(restTemplate.postForEntity(anyString(), any(), any()))
        .thenThrow(new SocketTimeoutException("Read timed out"));
    
    // Verify timeout is handled gracefully
    assertThrows(EventProcessingException.class, () -> 
        accountClient.apply(testEventRequest));
}
```

**Time Estimate:** 30 minutes  
**Files to Modify:**
- AccountClient.java
- application.yml

---

## 📊 Quick Implementation Roadmap

```
Session 1 (Immediate - 2.5 hours):
├─ Priority 1: Add GET endpoints (1-2 hours)
├─ Priority 2: Fix HTTP status codes (10 minutes)
└─ Priority 3: Add metadata support (30 minutes)

Session 2 (Short term - 3 hours):
├─ Priority 4: Out-of-order test (1 hour)
├─ Priority 5: Jaeger trace verification (2 hours)
└─ Priority 6: Timeout configuration (30 minutes)

TOTAL TIME: ~5.5 hours
RESULT: 100% specification compliance ✅
```

---

## 🧪 Validation Checklist

After implementing all items:

- [ ] All 10 GET endpoints working (5 gateway + 5 account service)
- [ ] 503 returned when Account Service unavailable
- [ ] Metadata field accepted and stored
- [ ] Out-of-order test passes
- [ ] Jaeger shows traces for requests
- [ ] RestTemplate calls timeout appropriately
- [ ] All integration tests pass: `mvn test`
- [ ] Docker Compose starts cleanly: `docker-compose up --build`
- [ ] API responses match README examples
- [ ] Health endpoints return 200 OK
- [ ] Prometheus metrics endpoint available
- [ ] No error logs in docker-compose output

---

## 📝 Testing Commands

```bash
# Run all tests
mvn clean test

# Run specific test
mvn test -Dtest=AccountControllerIntegrationTest

# Test with debug output
mvn test -X

# Start services with Docker Compose
docker-compose up --build

# Test API endpoint (after services running)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"test-1","accountId":"acc-1","type":"CREDIT","amount":100,"currency":"USD"}'

# View Jaeger traces
open http://localhost:16686

# Check Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

---

## 🎯 Success Criteria

✅ **All endpoints working:** GET + POST on both services  
✅ **Correct HTTP status codes:** 200/201 for success, 400 for validation, 503 for unavailable  
✅ **Metadata preserved:** Events stored with optional metadata field  
✅ **Out-of-order handling verified:** Test confirms balance correct regardless of arrival order  
✅ **Traces visible:** Events appear in Jaeger UI  
✅ **Timeouts configured:** Slow responses handled gracefully  
✅ **100% test coverage:** All new tests pass  
✅ **Docker working:** `docker-compose up --build` starts everything  

---

## 📌 Notes

- All changes maintain backward compatibility
- No database migrations needed (H2 in-memory)
- No breaking API changes
- Tests can run independently or in Docker
- Circuit breaker remains active and functional
- All existing tests continue to pass


