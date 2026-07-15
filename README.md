# Event Ledger Production

A distributed financial transaction processing system with event-sourcing architecture, demonstrating event idempotency, trace propagation, and resilient inter-service communication.

## Architecture Overview

The system consists of two microservices:

- **Gateway Service** (Port 8080): Event ingestion endpoint with idempotency guarantee
- **Account Service** (Port 8081): Transaction processing and balance calculation

```
Client → Gateway Service → Account Service → H2 Database
    ↓
  Jaeger (Distributed Tracing)
```

## Tech Stack

- **Framework**: Spring Boot 3.5.0
- **Java**: 21
- **Persistence**: Spring Data JPA + H2 (in-memory)
- **Resilience**: Resilience4j Circuit Breaker
- **Observability**: OpenTelemetry tracing with Jaeger
- **Monitoring**: Micrometer + Prometheus
- **Container**: Docker & Docker Compose
- **Testing**: JUnit 5 + Mockito

## Key Features

### 1. Event Idempotency
- Events are deduplicated at the database level using unique eventId constraint
- Duplicate requests return cached results without reprocessing
- Prevents double-charging in case of network retries

### 2. Transaction Processing
- **Credit**: Increases account balance
- **Debit**: Decreases account balance with overdraft protection
- Balance calculated as sum of all transactions using ACID semantics

### 3. Circuit Breaker Pattern
- Resilience4j circuit breaker protects Account Service calls
- Configurable thresholds: 50% failure rate, 100 call sliding window
- Automatic fallback with meaningful error messages

### 4. Distributed Tracing
- OpenTelemetry integration captures end-to-end request flow
- Jaeger UI displays service latencies, error rates, dependencies
- Trace context propagated across service boundaries
- Accessible at http://localhost:16686

### 5. Exception Handling
- Global @ControllerAdvice for consistent error responses
- Validation errors, insufficient funds, account not found all handled
- All errors include HTTP status, message, and timestamp

## Project Structure

```
event-ledger-production/
├── account-service/
│   ├── src/main/java/com/eventledger/account/
│   │   ├── controller/
│   │   │   └── AccountController.java
│   │   ├── service/
│   │   │   └── AccountService.java
│   │   ├── entity/
│   │   │   ├── Account.java
│   │   │   └── Transaction.java
│   │   ├── dto/
│   │   │   ├── AccountResponse.java
│   │   │   └── TransactionRequest.java
│   │   ├── exception/
│   │   │   ├── AccountException.java
│   │   │   ├── IdempotencyException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── repository/
│   ├── src/test/java/
│   │   └── integration & unit tests
│   ├── pom.xml
│   ├── Dockerfile
│   └── application.yml
│
├── gateway-service/
│   ├── src/main/java/com/eventledger/gateway/
│   │   ├── controller/
│   │   │   └── EventController.java
│   │   ├── service/
│   │   │   └── EventService.java
│   │   ├── entity/
│   │   │   └── EventEntity.java
│   │   ├── dto/
│   │   │   └── EventRequest.java
│   │   ├── client/
│   │   │   └── AccountClient.java
│   │   ├── exception/
│   │   │   ├── EventProcessingException.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── repository/
│   ├── src/test/java/
│   │   └── integration & unit tests
│   ├── pom.xml
│   ├── Dockerfile
│   └── application.yml
│
├── docker-compose.yml
└── README.md
```

## API Endpoints

### Gateway Service (Port 8080)

**Create Event**
```bash
POST /events
Content-Type: application/json

{
  "eventId": "EVT-2024-001",
  "accountId": "ACC-001",
  "type": "CREDIT",
  "amount": 100.50,
  "currency": "USD",
  "eventTimestamp": "2024-01-15T10:30:00Z"
}
```

Response (201 Created):
```json
{
  "eventId": "EVT-2024-001",
  "accountId": "ACC-001",
  "type": "CREDIT",
  "amount": 100.50,
  "currency": "USD",
  "eventTimestamp": "2024-01-15T10:30:00Z",
  "receivedAt": "2024-01-15T10:30:01Z",
  "processed": true
}
```

### Account Service (Port 8081)

**Get Account Balance**
```bash
GET /accounts/{accountId}/balance
```

Response:
```json
{
  "accountId": "ACC-001",
  "balance": 250.75,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z"
}
```

**Apply Transaction** (internal use)
```bash
POST /accounts/{accountId}/transactions
Content-Type: application/json

{
  "eventId": "EVT-2024-001",
  "type": "DEBIT",
  "amount": 50.00,
  "currency": "USD",
  "eventTimestamp": "2024-01-15T10:30:00Z"
}
```

### Monitoring Endpoints

**Account Service Metrics**
- Health: http://localhost:8081/actuator/health
- Prometheus: http://localhost:8081/actuator/prometheus

**Gateway Service Metrics**
- Health: http://localhost:8080/actuator/health
- Prometheus: http://localhost:8080/actuator/prometheus

**Distributed Tracing**
- Jaeger UI: http://localhost:16686

## Building & Running

### Prerequisites
- Docker & Docker Compose
- Maven 3.9+
- Java 21

### Option 1: Docker Compose (Recommended)

```bash
# Build and start all services (including Jaeger for tracing)
docker-compose up --build

# Services will be available at:
# - Gateway: http://localhost:8080
# - Account: http://localhost:8081
# - Jaeger: http://localhost:16686
```

### Option 2: Local Development

**Build both services:**
```bash
# Account Service
cd account-service
mvn clean package

# Gateway Service
cd gateway-service
mvn clean package
```

**Run services locally:**
```bash
# Terminal 1 - Account Service
cd account-service
mvn spring-boot:run

# Terminal 2 - Gateway Service
cd gateway-service
mvn spring-boot:run
```

### Option 3: Build Docker Images Manually

```bash
# Account Service
cd account-service
docker build -t event-ledger/account-service:1.0.0 .

# Gateway Service
cd gateway-service
docker build -t event-ledger/gateway-service:1.0.0 .

# Run with Docker Compose
docker-compose up
```

## Testing

### Run All Tests

```bash
# Account Service tests
cd account-service
mvn test

# Gateway Service tests
cd gateway-service
mvn test
```

### Run Specific Test Class

```bash
cd account-service
mvn test -Dtest=AccountServiceTest
mvn test -Dtest=AccountControllerIntegrationTest
```

### Test Coverage

- **Unit Tests**: Service logic with mocked dependencies
- **Integration Tests**: Full Spring Boot context with H2 in-memory DB
- **Idempotency Tests**: Duplicate event processing
- **Error Handling Tests**: Invalid inputs, insufficient funds, missing accounts

## Workflow Examples

### Example 1: Create Account with Credit

```bash
# 1. Create event (Gateway receives credit event)
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "EVT-001",
    "accountId": "ACC-123",
    "type": "CREDIT",
    "amount": 500.00,
    "currency": "USD"
  }'

# Expected response: Event created, transaction applied, balance now 500.00

# 2. Check balance
curl http://localhost:8081/accounts/ACC-123/balance

# Expected: { "accountId": "ACC-123", "balance": 500.00, ... }
```

### Example 2: Idempotent Processing

```bash
# Send same event twice
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{ "eventId": "EVT-002", "accountId": "ACC-123", ... }'

# First call: Creates event, applies transaction
# Second call: Returns cached result, balance unchanged (still 500.00 + new amount once)
```

### Example 3: Circuit Breaker Activation

If Account Service is down:
```bash
curl -X POST http://localhost:8080/events ...

# Response: 400 Bad Request
# { "status": 400, "message": "Account service unavailable - circuit breaker open" }
```

## Configuration

### Account Service (account-service/application.yml)
```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:h2:mem:accountdb
  jpa:
    hibernate:
      ddl-auto: create-drop

management:
  tracing:
    sampling:
      probability: 1.0
```

### Gateway Service (gateway-service/application.yml)
```yaml
server:
  port: 8080

account:
  service:
    url: http://localhost:8081

resilience4j:
  circuitbreaker:
    instances:
      accountService:
        failureRateThreshold: 50
        slidingWindowSize: 100
        waitDurationInOpenState: 1s
```

## Distributed Tracing

### Viewing Traces in Jaeger

1. Open http://localhost:16686
2. Select service from dropdown (gateway-service or account-service)
3. View trace for recent requests
4. Inspect spans to see:
   - Request latency
   - Service-to-service calls
   - Database operations
   - Error details

### Trace Context Headers

Trace context is automatically propagated via:
- `traceparent` (W3C Trace Context)
- `tracestate`
- `uber-trace-id` (Jaeger format)

## Database Schema

### Accounts Table
```sql
CREATE TABLE accounts (
  account_id VARCHAR(255) PRIMARY KEY,
  balance DECIMAL(19,2),
  version BIGINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

### Transactions Table
```sql
CREATE TABLE transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(255) UNIQUE NOT NULL,
  account_id VARCHAR(255),
  type VARCHAR(20),
  amount DECIMAL(19,2),
  currency VARCHAR(3),
  transaction_time TIMESTAMP,
  created_at TIMESTAMP
);
```

### Events Table
```sql
CREATE TABLE events (
  event_id VARCHAR(255) PRIMARY KEY,
  account_id VARCHAR(255),
  type VARCHAR(20),
  amount DECIMAL(19,2),
  currency VARCHAR(3),
  event_timestamp TIMESTAMP,
  metadata TEXT,
  received_at TIMESTAMP,
  processed BOOLEAN
);
```

## Troubleshooting

### Services not communicating
- Check logs: `docker logs account-service` or `docker logs gateway-service`
- Verify network: `docker network ls`
- Ensure ports 8080, 8081, 4317 are not in use

### Traces not appearing in Jaeger
- Verify Jaeger is running: http://localhost:16686
- Check OTEL_EXPORTER_OTLP_ENDPOINT environment variable
- Review service logs for export errors

### Tests failing
- Ensure H2 driver is in dependency path
- Check for port conflicts with local services
- Run with `mvn test -X` for debug output

## Performance Considerations

- **Circuit Breaker**: Prevents cascading failures
- **Idempotency**: Eliminates duplicate processing overhead
- **In-Memory Database**: Fast for testing; use RDS/PostgreSQL for production
- **Connection Pooling**: HikariCP handles concurrent requests
- **Transaction Isolation**: Using Spring @Transactional with default SERIALIZABLE

## Future Enhancements

- [ ] Event replay capability with event sourcing
- [ ] Audit logging of all transactions
- [ ] Multi-currency support with exchange rates
- [ ] Scheduled reconciliation jobs
- [ ] WebSocket support for real-time balance updates
- [ ] Rate limiting per account
- [ ] Fraud detection ML model integration
- [ ] PostgreSQL/RDS migration guide

## Contributing

1. Create feature branch from main
2. Write tests for new functionality
3. Ensure all tests pass: `mvn test`
4. Build Docker image: `docker-compose up --build`
5. Submit pull request with description

## License

Proprietary - Event Ledger System

