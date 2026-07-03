# BankingApp

A full-stack personal banking application.
Manage multiple accounts in different currencies, move money between them with fixed exchange rates, and browse a full transaction history.
BankingApp simulates a real-world banking backend paired with a responsive Angular frontend.

---

## Features

### Backend (Spring Boot)
- **Multi-currency accounts** — create accounts in EUR, USD, GBP, SEK, VND
- **Deposit & Withdrawal**
- **Currency exchange (transfer)** — fixed exchange rates fetched at transfer time; both legs (EXCHANGE_OUT / EXCHANGE_IN) recorded atomically
- **Cursor-based pagination** — transaction history loaded in pages of 5
- **External audit call** — real HTTP call to `httpbun.com` before every debit with a 3-second timeout; records SUCCESS / FAILED / TIMED_OUT without blocking the operation
- **Idempotency keys** — client-supplied UUID per request; duplicate calls return the cached result
- **User registration** — public endpoint to create a user account
- **HTTP Basic Auth** — stateless authentication on all protected endpoints
- **Flyway migrations** — versioned schema (V1–V10)

### Frontend (Angular 18)
- **Dashboard** — all accounts listed with currency symbols and live balances
- **Create account** — choose name and currency from the dashboard
- **Account overview** — balance history line chart, deposit / withdraw / transfer action buttons
- **Transfer flow** — target account selector, exchange rate preview, confirmation screen before execution
- **Infinite scroll** — transaction list auto-loads the next 5 on scroll
- **Transaction detail** — full breakdown including counterparty account, correlation ID, external audit status
- **PDF export** — download any transaction as a PDF receipt

---

## Requirements

| Tool | Minimum version |
|---|---|
| Java | 21 |
| Maven | 3.9 (or use the included `mvnw`) |
| Node.js | 20 |
| npm | 9 |
| PostgreSQL | 14 |
| Docker + Docker Compose | 24 *(only if running via Docker)* |

---

## Running locally (without Docker)

### 1. PostgreSQL setup

Install PostgreSQL and make sure it is running. The database `finance_app` is created automatically on first startup — no manual SQL step required.

### 2. Backend

```bash
cd finance-app

# Set your local PostgreSQL credentials
# Edit: src/main/resources/application-local.properties
#   spring.datasource.username=<your-pg-username>
#   spring.datasource.password=<your-pg-password>

./mvnw spring-boot:run
```

Flyway runs automatically on startup and applies all migrations (V1–V10).

Backend is available at `http://localhost:9090`.

### 3. Frontend

```bash
cd frontend
npm install
npm start
```

Frontend is available at `http://localhost:4200`.

---

## Running with Docker (recommended for a quick start)

```bash
# From the repo root
docker compose up --build
```

This starts three containers in order:

1. **PostgreSQL** — waits until healthy
2. **Spring Boot backend** — runs Flyway migrations, then starts on port 9090
3. **Angular frontend** — served by nginx on port 4200

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:9090 |
| PostgreSQL | localhost:5432 |

To stop:
```bash
docker compose down          # keep the database volume
docker compose down -v       # also delete the database volume
```

---

## Switching between local and Docker (backend only)

One line in `finance-app/src/main/resources/application.properties`:

```properties
spring.profiles.active=local    # uses application-local.properties  → localhost:5432
spring.profiles.active=docker   # uses application-docker.properties → db:5432 (Docker network)
```

When running via `docker compose`, the active profile is set automatically via the `SPRING_PROFILES_ACTIVE=docker` environment variable — no manual change needed.

---

## Demo users

Three pre-seeded accounts are available to explore the app immediately after startup:

| Username | Password |
|---|---|
| emma | Emma@1234 |
| liam | Liam@1234 |
| sofia | Sofia@1234 |

Each user has several accounts with transaction history already populated, so you can see the charts and pagination in action without any setup.

---

## API overview

All endpoints (except registration) require HTTP Basic Auth.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/users/register` | Register a new user |
| GET | `/api/v1/accounts` | List all accounts for the authenticated user |
| POST | `/api/v1/accounts` | Create a new account |
| GET | `/api/v1/accounts/{id}` | Get a single account |
| POST | `/api/v1/accounts/{id}/deposit` | Deposit funds |
| POST | `/api/v1/accounts/{id}/withdraw` | Withdraw funds |
| POST | `/api/v1/accounts/{id}/exchange` | Transfer to another account (with currency conversion) |
| GET | `/api/v1/accounts/{id}/transactions` | Paginated transaction history |
| GET | `/api/v1/accounts/{id}/transactions/{txId}` | Single transaction detail |
| GET | `/api/v1/exchange-rates` | Get exchange rate between two currencies |

---

## Project structure

```
finance-app/                   ← repo root
├── docker-compose.yml         ← boots db + backend + frontend
├── finance-app/               ← Spring Boot backend
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/          ← controllers, services, models, config
│           └── resources/
│               ├── application.properties
│               ├── application-local.properties
│               ├── application-docker.properties
│               └── db/migration/   ← Flyway V1–V10
└── frontend/                  ← Angular 18 app
    ├── Dockerfile
    ├── nginx.conf
    └── src/
        └── app/
            ├── pages/         ← login, home, account-overview, transaction-overview
            ├── store/         ← NgRx store (accounts, transactions, auth)
            └── core/          ← services, models
```
