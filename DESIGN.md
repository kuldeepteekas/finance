# Finance App — Backend Design & Grooming Document

> Status: Grooming Draft v1.1
> Scope: Java Spring Boot backend only (Angular frontend is separate)
> DB: PostgreSQL

---

## 1. Scope & Goals

Build a self-contained banking microservice that allows users to:
- Manage multiple accounts (each with a single currency)
- Deposit and withdraw money
- Exchange money between their own accounts (with fixed currency rates)
- View paginated transaction history
- Have every debit preceded by an external audit/logging call

Non-goals (for now):
- Admin panel
- User registration (users are pre-seeded)
- Account closing/deactivation
- PDF export (frontend concern)
- Real-time exchange rate feeds

---

## 2. Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Java 17 | LTS, modern features (records, sealed classes) |
| Framework | Spring Boot 3.x | Industry standard, auto-config, Security, JPA |
| Database | PostgreSQL | ACID, row-level locking, good for financial data |
| ORM | Spring Data JPA + Hibernate | Standard persistence layer |
| Security | Spring Security — HTTP Basic Auth | Simple, sufficient for this scope |
| HTTP Client | Spring WebClient (reactive) | Non-blocking, good for external calls with timeout |
| Migration | Flyway | Schema versioning, seed data |
| Build | Maven | Standard Java build tool |

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        REST API Layer                        │
│              (Controllers — input validation)                │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                      Service Layer                           │
│   (Business logic, transaction orchestration, locking)       │
└──────┬────────────────────┬───────────────────┬─────────────┘
       │                    │                   │
┌──────▼──────┐   ┌─────────▼───────┐  ┌───────▼────────────┐
│  Repository │   │ Idempotency     │  │ External Audit     │
│  Layer      │   │ Service         │  │ Service            │
│  (JPA)      │   │ (dedup writes)  │  │ (httpstat.us call) │
└──────┬──────┘   └─────────────────┘  └────────────────────┘
       │
┌──────▼──────┐
│ PostgreSQL  │
└─────────────┘
```

### Layer Responsibilities

- **Controller**: Parse request, validate input, delegate to service, return response
- **Service**: All business logic, orchestrate DB + external calls, own transactions
- **Repository**: JPA interfaces only, no business logic
- **Idempotency Service**: Check/store idempotency keys before mutating operations
- **External Audit Service**: Async HTTP call to external system before debit

---

## 4. Data Model

### 4.1 users

```
users
├── id              UUID (PK)
├── username        VARCHAR(100) UNIQUE NOT NULL
├── password        VARCHAR(255) NOT NULL   -- BCrypt hashed
├── full_name       VARCHAR(255)
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL
```

> Pre-seeded via Flyway migration. No registration API for now.
> Password stored as BCrypt hash (Spring Security default).

---

### 4.2 accounts

```
accounts
├── id              UUID (PK)
├── user_id         UUID FK → users.id NOT NULL
├── currency        VARCHAR(3) NOT NULL       -- EUR, USD, SEK, GBP, VND
├── balance         NUMERIC(19,4) NOT NULL DEFAULT 0
├── status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE, CLOSED, BLOCKED, INACTIVE
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

Constraints:
- balance >= 0  (CHECK constraint at DB level)
- currency IN ('EUR','USD','SEK','GBP','VND')
- status IN ('ACTIVE','CLOSED','BLOCKED','INACTIVE')

Indexes:
- idx_accounts_user_id ON accounts(user_id)
```

> One account = one currency. A user CAN have multiple accounts with the same currency.
> Balance can never go negative (enforced at DB + service layer).
> Status field is present for model completeness (future states: CLOSED, BLOCKED, INACTIVE).
> Business logic for status transitions (closing, blocking) is out of scope for this version.
> All accounts are created with status=ACTIVE. No status-change endpoint implemented yet.

---

### 4.3 transactions

```
transactions
├── id                UUID (PK)
├── account_id        UUID FK → accounts.id NOT NULL
├── user_id           UUID FK → users.id NOT NULL
├── type              VARCHAR(20) NOT NULL     -- DEPOSIT, WITHDRAWAL, EXCHANGE_OUT, EXCHANGE_IN
├── amount            NUMERIC(19,4) NOT NULL
├── currency          VARCHAR(3) NOT NULL
├── balance_before    NUMERIC(19,4) NOT NULL
├── balance_after     NUMERIC(19,4) NOT NULL
├── status            VARCHAR(20) NOT NULL     -- PENDING, SUCCESS, FAILED
├── description       VARCHAR(500)
├── failure_reason    VARCHAR(500)
├── correlation_id    UUID NOT NULL            -- groups related transactions (e.g. exchange pair)
├── idempotency_key   VARCHAR(255)             -- dedup key from client
├── external_call_status VARCHAR(20)           -- SUCCESS, FAILED, SKIPPED (only for WITHDRAWAL)
├── created_at        TIMESTAMP NOT NULL
└── updated_at        TIMESTAMP NOT NULL

Indexes:
- idx_transactions_account_id ON transactions(account_id)
- idx_transactions_correlation_id ON transactions(correlation_id)
- idx_transactions_idempotency_key ON transactions(idempotency_key)
- idx_transactions_created_at ON transactions(created_at DESC)   -- for pagination
```

> Every exchange creates TWO transaction records (EXCHANGE_OUT + EXCHANGE_IN) sharing the same correlation_id.
> Failed transactions ARE persisted (audit trail).

---

### 4.4 exchange_rates

```
exchange_rates
├── id              UUID (PK)
├── from_currency   VARCHAR(3) NOT NULL
├── to_currency     VARCHAR(3) NOT NULL
├── rate            NUMERIC(19,6) NOT NULL    -- multiply from_amount by rate to get to_amount
├── effective_from  TIMESTAMP NOT NULL
├── created_at      TIMESTAMP NOT NULL

Unique constraint: (from_currency, to_currency, effective_from)

Indexes:
- idx_exchange_rates_pair ON exchange_rates(from_currency, to_currency, effective_from DESC)
```

> Seeded via Flyway with initial rates for all currency pairs.
> Service always uses the latest rate (MAX effective_from) for a given pair.
> Rate history is kept (never deleted) for audit.

---

### 4.5 idempotency_keys

```
idempotency_keys
├── id              UUID (PK)
├── key             VARCHAR(255) UNIQUE NOT NULL
├── user_id         UUID FK → users.id
├── response_status INT NOT NULL
├── response_body   TEXT NOT NULL             -- JSON snapshot of the response
├── created_at      TIMESTAMP NOT NULL
└── expires_at      TIMESTAMP NOT NULL        -- 24h TTL

Indexes:
- idx_idempotency_keys_key ON idempotency_keys(key)
- idx_idempotency_keys_expires_at ON idempotency_keys(expires_at)  -- for cleanup job
```

---

## 5. API Design

Base path: `/api/v1`
Auth: HTTP Basic on every request (Spring Security filter chain)

---

### 5.1 Account Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/accounts` | Create a new account for authenticated user |
| GET | `/accounts` | List all accounts for authenticated user |
| GET | `/accounts/{accountId}` | Get a single account |

**POST /accounts — Request**
```json
{
  "currency": "EUR"
}
```

**POST /accounts — Response 201**
```json
{
  "id": "uuid",
  "currency": "EUR",
  "balance": 0.0000,
  "status": "ACTIVE",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

---

### 5.2 Money Operations

| Method | Path | Description |
|---|---|---|
| POST | `/accounts/{accountId}/deposit` | Add money to account |
| POST | `/accounts/{accountId}/withdraw` | Debit money from account |
| POST | `/accounts/{accountId}/exchange` | Exchange to another account |

**Headers for mutating operations:**
```
Idempotency-Key: <client-generated UUID>   -- required for ALL three: deposit, withdraw, exchange
```

> Why deposit too? If a deposit request times out and the client retries, without an idempotency key
> the account gets credited twice. Same risk as withdrawal — all money operations must be idempotent.

**POST /accounts/{accountId}/deposit — Request**
```json
{
  "amount": 500.00,
  "description": "Initial deposit"
}
```

**POST /accounts/{accountId}/withdraw — Request**
```json
{
  "amount": 100.00,
  "description": "ATM withdrawal"
}
```

**POST /accounts/{accountId}/exchange — Request**
```json
{
  "targetAccountId": "uuid-of-target-account",
  "amount": 200.00,
  "description": "Sending to EUR account"
}
```

**Response for all money operations — 200**
```json
{
  "transactionId": "uuid",
  "correlationId": "uuid",
  "status": "SUCCESS",
  "amount": 200.00,
  "currency": "EUR",
  "balanceBefore": 500.00,
  "balanceAfter": 300.00,
  "createdAt": "2024-01-01T10:00:00Z"
}
```

---

### 5.3 Transaction Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/accounts/{accountId}/transactions` | Paginated transaction history |
| GET | `/accounts/{accountId}/transactions/{transactionId}` | Single transaction detail |

**GET /accounts/{accountId}/transactions — Query Params**
```
?cursor=<last-transaction-id>    -- for cursor-based pagination (infinite scroll)
&limit=20                        -- default 20, max 100
```

> Cursor-based pagination (not offset) — stable under concurrent writes, better for infinite scroll.

**GET /accounts/{accountId}/transactions — Response 200**
```json
{
  "data": [
    {
      "id": "uuid",
      "type": "WITHDRAWAL",
      "amount": 100.00,
      "currency": "EUR",
      "balanceBefore": 500.00,
      "balanceAfter": 400.00,
      "status": "SUCCESS",
      "description": "ATM withdrawal",
      "correlationId": "uuid",
      "createdAt": "2024-01-01T10:00:00Z"
    }
  ],
  "nextCursor": "uuid-of-last-item",
  "hasMore": true
}
```

---

### 5.4 Exchange Rate Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/exchange-rates` | Get all current exchange rates |
| GET | `/exchange-rates/{from}/{to}` | Get rate for a specific pair |

---

## 6. Core Business Logic Flows

### 6.1 Deposit Flow

```
1. Authenticate user (Basic Auth filter)
2. Check idempotency key — if exists, return cached response immediately
3. Validate request (amount > 0, account exists, account is ACTIVE)
4. Verify account belongs to authenticated user
5. Begin DB transaction (@Transactional)
   a. Lock account row (SELECT FOR UPDATE — pessimistic)
   b. Record balance_before
   c. Update account balance (+amount)
   d. Record balance_after
   e. Persist transaction record (status=SUCCESS)
6. Commit
7. Store idempotency key + response
8. Return response
```

---

### 6.2 Withdrawal Flow

```
1. Authenticate user
2. Check idempotency key — if exists, return cached response immediately
3. Validate request (amount > 0, account exists, ACTIVE, currency matches account)
4. Verify account belongs to authenticated user
5. Call external audit system (with 3s timeout)
   - Fire the HTTP call, wait up to 3s
   - Record result: external_call_status = SUCCESS or FAILED
   - Proceed regardless of outcome
6. Begin DB transaction (@Transactional)
   a. Lock account row (SELECT FOR UPDATE — pessimistic)
   b. Check balance >= amount
      → If insufficient: persist FAILED transaction record, commit, return 422
   c. Record balance_before
   d. Update balance (-amount)
   e. Record balance_after
   f. Persist transaction record with status=SUCCESS, external_call_status from step 5
7. Commit
8. Store idempotency key + response
9. Return response
```

> No PENDING state. Transaction is written exactly once as either SUCCESS or FAILED.
> A scheduled cleanup job for stuck PENDINGs is deferred to a future version (Option A).
> The external audit call happens BEFORE the DB transaction opens — keeps the lock window short.

---

### 6.3 Currency Exchange Flow

```
1. Authenticate user
2. Check idempotency key
3. Validate: source account ACTIVE, target account ACTIVE, both belong to user
4. Validate: source account currency != target account currency (no point exchanging same currency)
5. Fetch latest exchange rate for (source.currency → target.currency)
6. Calculate target_amount = amount * rate
7. Generate shared correlation_id for the pair

8. Begin DB transaction (@Transactional)
   a. LOCK ACCOUNTS IN CONSISTENT ORDER:
      - Sort [source_account_id, target_account_id] lexicographically
      - Lock lower ID first, then higher ID
      (prevents deadlock when two exchanges race in opposite directions)
   b. Check source balance >= amount
   c. Deduct amount from source account
   d. Add target_amount to target account
   e. Persist EXCHANGE_OUT transaction on source (status=SUCCESS)
   f. Persist EXCHANGE_IN transaction on target (status=SUCCESS)
9. Commit
10. Store idempotency key
11. Return response (source transaction ID, rate used, amount deducted, target amount credited)
```

---

### 6.4 External Audit Call

- Called via **Spring WebClient** before every debit/exchange
- Timeout: **3 seconds**
- Fire and record — transaction proceeds regardless
- On success: `external_call_status = SUCCESS`
- On timeout/error: `external_call_status = FAILED`, log warning
- Target: `https://httpstat.us/200` (simulates successful logging endpoint)

```
Design note: This is treated as a non-blocking audit log. 
In a real system this might be a fraud/AML check (hard dependency).
We've scoped it as soft dependency per grooming decision.
```

---

## 7. Concurrency & Locking Strategy

### Approach: Pessimistic Locking

Use `SELECT ... FOR UPDATE` at the PostgreSQL level via JPA:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Account> findById(UUID id);
```

### Why not Optimistic Locking?
- Optimistic locking requires retry logic on `OptimisticLockException`
- Under load, retries can cascade and still deadlock
- For financial operations, pessimistic is simpler to reason about and safer

### Deadlock Prevention for Exchange
- **Rule**: Always acquire account locks in ascending order of account ID (UUID lexicographic order)
- This guarantees no two threads can acquire locks in opposite order
- Example: Exchange A→B and concurrent Exchange B→A will both try to lock A first

### Transaction Isolation
- Use `REPEATABLE READ` isolation for all financial operations
- Prevents phantom reads during balance checks

### Thread Safety
- No shared mutable state in services (all state in DB)
- `@Transactional` ensures atomicity
- Connection pool (HikariCP default) handles concurrent requests

---

## 8. Security Design

### Authentication
- HTTP Basic Auth via Spring Security
- Every request must include `Authorization: Basic <base64(user:password)>` header
- Credentials validated against `users` table (BCrypt password match)
- Stateless — no sessions, no cookies

### Authorization
- Users can only access **their own accounts and transactions**
- Account ownership validated in service layer on every operation:
  ```
  if (!account.getUserId().equals(authenticatedUserId)) throw AccessDeniedException
  ```
- No user can see or mutate another user's data

### Data Security
- Passwords stored as BCrypt hash (never plaintext)
- No sensitive data in logs (mask amounts if needed in prod)
- DB user for app has minimal privileges (no DROP, no DDL)

### Input Validation
- All request bodies validated with Bean Validation (`@Valid`, `@NotNull`, `@Positive`)
- Currency enum validated — reject unknown currencies with 400
- Amount scale validated — max 4 decimal places

### Future Considerations (out of scope now)
- Move to JWT for stateless token auth
- Rate limiting per user (prevent abuse)
- HTTPS enforced at infra level

---

## 9. Failure Handling & Resilience

### Scenario Matrix

| Scenario | Behavior |
|---|---|
| Insufficient funds | 422 response, FAILED transaction persisted |
| External audit call timeout | Log warning, continue, mark `external_call_status=FAILED` |
| External audit call error (5xx) | Same as timeout |
| DB connection lost mid-transaction | Transaction rolled back, 503 returned |
| Duplicate request (same idempotency key) | Return original cached response, no second write |
| Account does not exist | 404 Not Found |
| Exchange rate not found | 400 Bad Request |
| Account balance goes negative (race condition) | Prevented by `SELECT FOR UPDATE` + DB CHECK constraint as last line of defense |
| DB CHECK constraint violation | 500 caught, log alert (should never happen if service logic is correct) |

### Error Response Format (consistent across all endpoints)
```json
{
  "error": {
    "code": "INSUFFICIENT_FUNDS",
    "message": "Account balance is insufficient for this operation",
    "correlationId": "uuid",
    "timestamp": "2024-01-01T10:00:00Z"
  }
}
```

### Error Codes
| Code | HTTP Status | Description |
|---|---|---|
| `INSUFFICIENT_FUNDS` | 422 | Balance too low for debit/exchange |
| `ACCOUNT_NOT_FOUND` | 404 | Account ID does not exist |
| `ACCOUNT_NOT_ACTIVE` | 409 | Account does not exist or is not accessible |
| `ACCESS_DENIED` | 403 | Account belongs to different user |
| `IDEMPOTENCY_CONFLICT` | 200 | Duplicate request, cached response returned |
| `EXCHANGE_RATE_NOT_FOUND` | 400 | No rate configured for currency pair |
| `SAME_CURRENCY_EXCHANGE` | 400 | Source and target account have same currency |
| `INVALID_INPUT` | 400 | Validation error |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## 10. Package Structure

```
com.financeapp
├── config/
│   ├── SecurityConfig.java          -- Spring Security Basic Auth setup
│   ├── WebClientConfig.java         -- External HTTP client config
│   └── JpaConfig.java               -- Transaction isolation config
│
├── domain/
│   ├── user/
│   │   ├── User.java                -- Entity
│   │   └── UserRepository.java
│   ├── account/
│   │   ├── Account.java             -- Entity
│   │   ├── AccountRepository.java
│   │   ├── AccountService.java
│   │   └── AccountController.java
│   ├── transaction/
│   │   ├── Transaction.java         -- Entity
│   │   ├── TransactionRepository.java
│   │   ├── TransactionService.java
│   │   └── TransactionController.java
│   └── exchangerate/
│       ├── ExchangeRate.java        -- Entity
│       └── ExchangeRateRepository.java
│
├── service/
│   ├── MoneyOperationService.java   -- Core debit/deposit/exchange orchestration
│   ├── IdempotencyService.java      -- Idempotency key check/store
│   └── ExternalAuditService.java    -- httpstat.us call
│
├── api/
│   ├── dto/                         -- Request/Response DTOs
│   └── exception/
│       ├── GlobalExceptionHandler.java   -- @RestControllerAdvice
│       └── domain exceptions...
│
└── infrastructure/
    └── db/
        └── migrations/              -- Flyway SQL files
            ├── V1__create_users.sql
            ├── V2__create_accounts.sql
            ├── V3__create_transactions.sql
            ├── V4__create_exchange_rates.sql
            ├── V5__create_idempotency_keys.sql
            └── V6__seed_data.sql    -- Users + exchange rates
```

---

## 11. Key Design Decisions Log

| Decision | Choice | Reason |
|---|---|---|
| Auth mechanism | HTTP Basic | Simple, sufficient for scope; can upgrade to JWT later |
| Locking strategy | Pessimistic (`SELECT FOR UPDATE`) | Financial ops need guaranteed consistency |
| Deadlock prevention | Lock by ascending account ID | Deterministic ordering prevents deadlock cycles |
| Exchange rates storage | DB table (seeded) | Updatable without redeployment; audit history |
| Idempotency | Client-provided key + DB store (24h TTL) | Prevents double-credit/debit on retry — applies to deposit, withdraw, exchange |
| Failed transactions | Persisted with status=FAILED | Full audit trail |
| External call on failure | Log + proceed (soft dependency) | It's an audit log, not a fraud check |
| External call placement | Before DB transaction opens | Keeps the DB lock window as short as possible |
| Transaction write strategy | Option B — write once as SUCCESS or FAILED, no PENDING | Simpler; PENDING + cleanup job deferred to future version |
| Account status field | Present in model (ACTIVE/CLOSED/BLOCKED/INACTIVE) | Future-proof; no status-transition business logic in this version |
| Multiple accounts same currency | Allowed | No unique constraint on (user_id, currency) |
| Pagination | Cursor-based (not offset) | Stable under concurrent writes; better for infinite scroll |
| Balance precision | NUMERIC(19,4) | 4 decimal places covers all 5 currencies; no float rounding issues |
| Transaction isolation | REPEATABLE READ | Prevents phantom reads during balance checks |

---

## 12. Open Items / Future Scope

- [ ] Move from Basic Auth → JWT with refresh tokens
- [ ] Admin role (view all users, all accounts)
- [ ] Account status transitions (close, block, reactivate) with business rules
- [ ] PENDING transaction state + scheduled cleanup job for stuck transactions (Option A)
- [ ] Rate limiting per user (Bucket4j or Redis)
- [ ] Soft-delete for users
- [ ] Exchange rate API integration (replace fixed rates)
- [ ] Event publishing (e.g., Kafka) for downstream consumers
- [ ] Prometheus metrics + health endpoints
- [ ] Scheduled job to clean up expired idempotency keys

---

*Last updated: Grooming Session 3*
