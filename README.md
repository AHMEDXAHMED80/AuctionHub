<div align="center">

<!-- Animated Header Banner -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,11,20&height=200&section=header&text=AuctionHub&fontSize=80&fontColor=fff&animation=twinkling&fontAlignY=35&desc=Real-Time%20Auction%20Platform%20Backend&descAlignY=55&descAlign=50" width="100%"/>

<!-- Typing Animation -->
<a href="https://git.io/typing-svg">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=22&pause=1000&color=6C63FF&center=true&vCenter=true&multiline=true&width=700&height=100&lines=Event-Driven+Auction+Platform;Built+with+Spring+Boot+%2B+Kafka+%2B+Redis;Secure+%7C+Scalable+%7C+Production-Ready" alt="Typing SVG" />
</a>

<br/>

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)

[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.8-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)](https://kafka.apache.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![Stripe](https://img.shields.io/badge/Stripe-Payment-008CDD?style=for-the-badge&logo=stripe&logoColor=white)](https://stripe.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)

[![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)](https://jwt.io/)
[![Nginx](https://img.shields.io/badge/Nginx-Proxy-009639?style=for-the-badge&logo=nginx&logoColor=white)](https://nginx.org/)
[![MapStruct](https://img.shields.io/badge/MapStruct-1.5.5-A32700?style=for-the-badge)](https://mapstruct.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

</div>

---

## Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Features](#-features)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Event-Driven Architecture](#-event-driven-architecture)
- [Data Models](#-data-models)
- [Security](#-security)
- [Rate Limiting](#-rate-limiting)
- [Infrastructure](#-infrastructure)
- [Testing](#-testing)
- [Contributing](#-contributing)

---

## 🌟 Overview

**AuctionHub** is a production-grade, real-time auction platform backend. Users list items for auction, compete in live bidding wars, manage payments through an integrated wallet, and receive instant notifications — all backed by a resilient, event-driven microservice-style architecture.

```
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT / FRONTEND                            │
│                  (React @ localhost:5173)                       │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP / WebSocket
                 ┌─────────▼─────────┐
                 │      NGINX         │
                 │  Reverse Proxy     │
                 │  Rate: 10r/s       │
                 └─────────┬─────────┘
                           │
                 ┌─────────▼─────────┐
                 │   SPRING BOOT      │
                 │   Port 8080        │
                 └──┬─────┬──────┬───┘
                    │     │      │
         ┌──────────┘     │      └────────────┐
         │                │                  │
┌────────▼────┐  ┌────────▼────────┐  ┌──────▼──────┐
│ PostgreSQL  │  │  Redis           │  │    Kafka    │
│  Port 5432  │  │  Port 6379       │  │  Port 9092  │
└─────────────┘  │  Cache + Tokens  │  └──────┬──────┘
                 │  + Leaderboard   │         │
                 └──────────────────┘  ┌──────▼──────┐
                                       │  RabbitMQ   │
                                       │  Port 5672  │
                                       └─────────────┘
```

---

## 🏛 Architecture

AuctionHub uses a **layered, event-driven architecture** with two brokers working together:

- **Apache Kafka** — high-throughput event streaming (bids, auction lifecycle)
- **RabbitMQ** — reliable notification & email delivery
- **Redis** — sub-millisecond caching, token blacklist, Sorted Set leaderboards

### Auction Lifecycle State Machine

```
  [PENDING] ──(start date)──► [ACTIVE]
                                  │
                           (end date reached)
                                  │
                            [PROCESSING]  ◄── Kafka: auction-ended
                                  │
             ┌────────────────────┼─────────────────┐
             │                   │                  │
       (bids found)          (no bids)          (error)
             │                   │                  │
          [SOLD]             [EXPIRED]         [CANCELLED]
```

---

## ⚙ Tech Stack

<div align="center">

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| Language | Java | 21 | Runtime |
| Framework | Spring Boot | 4.0.0 | Web, JPA, Security, Actuator |
| Database | PostgreSQL | 16 | Primary persistence |
| Cache | Redis | 7 | Token store, leaderboards |
| Event Streaming | Apache Kafka | 3.8.0 (KRaft) | Bid & auction events |
| Message Queue | RabbitMQ | 3.13 | Notifications & email |
| Payments | Stripe SDK | 24.10.0 | PaymentIntent API |
| Auth | JWT (JJWT) | 0.12.6 | Stateless, cookie-based |
| Object Mapping | MapStruct | 1.5.5 | Entity ↔ DTO |
| Rate Limiting | Bucket4j | 8.16.0 | IP-based throttling |
| Reverse Proxy | Nginx | latest | Load balancing |
| Containers | Docker Compose | — | Orchestration |
| Monitoring | Spring Actuator | — | Health & metrics |
| WebSocket | Spring WebSocket | 4.0 | Real-time updates |

</div>

---

## ✨ Features

<details>
<summary><b>🔐 Authentication & Authorization</b></summary>
<br>

- JWT with **access + refresh token rotation** (15 min / 7 day TTL)
- Tokens in **HTTP-only Secure cookies** — no localStorage exposure
- **Redis-backed token blacklist** — instant logout across all sessions
- Role-based access: `USER` · `POSTER` · `ADMIN`
- BCrypt password hashing
- Username & email availability checks before registration

</details>

<details>
<summary><b>🏷 Auction & Item Management</b></summary>
<br>

- **13 item categories**: Electronics, Furniture, Clothing, Books, Art, Vehicles, Real Estate, Jewelry, Collectibles, Sports, Home & Garden, Toys, Other
- Full item lifecycle: `PENDING → ACTIVE → PROCESSING → SOLD / EXPIRED / CANCELLED`
- **Automated auction expiration** — cron scheduler runs every minute, batch-updates expired items
- Multiple images per item with index-based ordering and swap endpoints
- Paginated search with filters (category, price range, keyword) and sorting
- **Redis Sorted Set leaderboard** for top 5 most-viewed items

</details>

<details>
<summary><b>💸 Bidding System</b></summary>
<br>

- Real-time bid placement via **Kafka event pipeline**
- Wallet **fund freezing** on every bid — released automatically when outbid
- **Optimistic locking** on Bid, Item, and Wallet entities (prevents race conditions)
- Bid status: `ACTIVE → WON / LOST / CANCELLED`
- Winning status checks, highest bid queries, per-item and per-user history

</details>

<details>
<summary><b>💳 Wallet & Payments</b></summary>
<br>

- Dual wallet types: `BIDDER` · `SELLER`
- Three-bucket balance: **Available** · **Frozen** · **Total Spent**
- Stripe **PaymentIntent** with async webhook resolution
- Payment lifecycle: `INITIATED → PROCESSING → SUCCEEDED / FAILED / CANCELED / REFUNDED`
- Max deposit: `$10,000` per transaction

</details>

<details>
<summary><b>🔔 Notifications</b></summary>
<br>

- RabbitMQ-driven in-app notifications (bid outbid, auction won/lost, payment updates)
- Paginated list, newest first
- Bulk mark-all-as-read

</details>

<details>
<summary><b>🛡 Security & Reliability</b></summary>
<br>

- IP-based rate limiting — 10 req/30min (auth), 100 req/30min (general)
- CSRF protection with smart bypass for Stripe webhooks
- CORS configured for frontend
- Global exception handler — consistent error shape
- WebSocket support for real-time bid updates

</details>

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version |
|------|---------|
| Java JDK | 21+ |
| Maven | 3.8+ |
| Docker Desktop | Latest |

### Environment Setup

```bash
git clone https://github.com/AHMED80XX/auctionhub.git
cd auctionhub
cp .env.example .env
```

Edit `.env` with your values:

```env
# Database
DB_URL=jdbc:postgresql://localhost:5432/auctionhub
DB_USERNAME=postgres
DB_PASSWORD=your_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT  (generate: openssl rand -base64 32)
JWT_SECRET=your_256bit_base64_secret

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# Stripe
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Cookies
AUTH_COOKIE_SECURE=false      # true in production (HTTPS)
AUTH_COOKIE_SAME_SITE=Lax
```

### Running with Docker

```bash
# Start full stack (Postgres, Redis, Kafka, RabbitMQ, App, Nginx)
docker compose up -d

# Tail logs
docker compose logs -f app

# Stop
docker compose down
```

**Service URLs:**

| Service | URL | Credentials |
|---------|-----|-------------|
| API (via Nginx) | `http://localhost:80` | — |
| Spring Boot (direct) | `http://localhost:8080` | — |
| PgAdmin | `http://localhost:5050` | `admin@admin.com` / `admin` |
| RabbitMQ UI | `http://localhost:15672` | `guest` / `guest` |
| Health Check | `http://localhost:8080/actuator/health` | — |

### Running Locally

```bash
# Start infrastructure only
docker compose up -d postgres redis kafka rabbitmq

# Build & run app
./mvnw clean package -DskipTests
java -jar target/auctionhub-*.jar

# (Optional) Forward Stripe webhooks locally
stripe listen --forward-to localhost:8080/api/stripe/webhook
```

---

## 📡 API Reference

Base URL: `http://localhost:8080`

Authentication is **cookie-based** — tokens are set automatically after login, no manual header management needed.

---

### Authentication — `/api/auth`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/auth/register` | ✖ | Register new user |
| `POST` | `/api/auth/login` | ✖ | Authenticate, receive JWT cookies |
| `POST` | `/api/auth/logout` | ✔ | Blacklist tokens, clear cookies |
| `POST` | `/api/auth/refresh` | ✔ | Rotate access + refresh tokens |
| `GET` | `/api/auth/check-username/{username}` | ✖ | Username availability |
| `GET` | `/api/auth/check-email/{email}` | ✖ | Email availability |

<details>
<summary>Example: Register</summary>

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "birthDate": "1990-01-15"
}
```

```json
// 201 Created
{ "message": "User registered successfully" }
```

</details>

<details>
<summary>Example: Login</summary>

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "johndoe",
  "password": "SecurePass123!"
}
```

```json
// 200 OK — sets accessToken + refreshToken HTTP-only cookies
{
  "username": "johndoe",
  "role": "USER"
}
```

</details>

---

### Items — Seller Management — `/api/items`

> Requires `POSTER` or `ADMIN` role.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/items` | Create auction listing |
| `GET` | `/api/items/user?page=0&size=10` | Your listings (paginated) |
| `PUT` | `/api/items/{itemId}` | Edit item (only while `PENDING`) |
| `PUT` | `/api/items/{itemId}/images/{imageId}` | Replace an image |
| `DELETE` | `/api/items/{itemId}/images/{imageId}` | Remove an image |
| `POST` | `/api/items/{itemId}/images/swap` | Reorder two images |

<details>
<summary>Example: Create Item</summary>

```http
POST /api/items
Content-Type: application/json

{
  "title": "Vintage Gibson Les Paul 1959",
  "description": "Excellent condition, original hardware",
  "category": "COLLECTIBLES",
  "startingPrice": 5000.00,
  "startDate": "2026-05-10T10:00:00",
  "endDate": "2026-05-17T18:00:00",
  "images": ["https://cdn.example.com/photo1.jpg"]
}
```

```json
// 201 Created
{ "itemId": 42, "status": "PENDING" }
```

</details>

---

### Items — Bidder Discovery — `/api/items`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/items/search` | ✖ | Search active listings |
| `GET` | `/api/items/top-viewed` | ✖ | Top 5 most viewed (Redis leaderboard) |
| `GET` | `/api/items/{itemId}/detail` | ✖ | Full item detail + images + bids |

<details>
<summary>Example: Search</summary>

```http
POST /api/items/search
Content-Type: application/json

{
  "keyword": "guitar",
  "category": "COLLECTIBLES",
  "minPrice": 1000,
  "maxPrice": 10000,
  "sortBy": "endDate",
  "sortDirection": "ASC",
  "page": 0,
  "size": 20
}
```

All fields are optional — omit any to broaden results.

</details>

---

### Bidding — `/api/bids`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/bids` | ✔ | Place a bid |
| `GET` | `/api/bids/item/{itemId}` | ✖ | Bid history for item (paginated) |
| `GET` | `/api/bids/item/{itemId}/highest` | ✖ | Current highest bid amount |
| `GET` | `/api/bids/item/{itemId}/winner` | ✖ | Current highest bidder username |
| `GET` | `/api/bids/item/{itemId}/am-i-winning` | ✔ | Check if you are winning |
| `GET` | `/api/bids/my-bids` | ✔ | All your bids (paginated) |
| `GET` | `/api/bids/active` | ✔ | Your bids on live auctions |
| `GET` | `/api/bids/history` | ✔ | Your bids on ended auctions |

<details>
<summary>Example: Place Bid</summary>

```http
POST /api/bids
Content-Type: application/json

{
  "itemId": 42,
  "bidAmount": 5500.00
}
```

```json
// 201 Created
{
  "bidId": 101,
  "status": "ACTIVE",
  "frozenAmount": 5500.00
}
```

**Rules:**
- Bid must exceed the current highest bid
- Bidder must have sufficient available balance
- Funds are **frozen** instantly on success
- Previous highest bidder's funds are **released** automatically

</details>

---

### Wallet — `/api/wallet`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/wallet` | Create wallet (`BIDDER` or `SELLER`) |
| `GET` | `/api/wallet` | Get wallet info |
| `GET` | `/api/wallet/balance` | Available + frozen + total spent |
| `POST` | `/api/wallet/deposit` | Add funds (max `$10,000`) |

<details>
<summary>Example: Get Wallet</summary>

```json
// 200 OK
{
  "walletId": 5,
  "walletType": "BIDDER",
  "availableBalance": 1500.00,
  "frozenBalance": 500.00,
  "totalSpend": 3200.00
}
```

</details>

---

### Payments — `/api/payments`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/payments` | Initiate Stripe PaymentIntent |
| `GET` | `/api/payments/history` | Payment history (paginated) |
| `GET` | `/api/payments/{paymentId}` | Payment details |
| `POST` | `/api/payments/{paymentId}/cancel` | Cancel pending payment |
| `POST` | `/api/payments/{paymentId}/refund` | Refund succeeded payment |

<details>
<summary>Example: Initiate Payment</summary>

```http
POST /api/payments
Content-Type: application/json

{ "amount": 2500.00 }
```

```json
// 201 Created
{
  "paymentId": 88,
  "clientSecret": "pi_3ABC...secret_xyz",
  "status": "INITIATED"
}
```

Pass `clientSecret` to Stripe.js on the frontend to confirm the payment.

</details>

---

### Notifications — `/api/notifications`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/notifications?page=0&size=20` | Paginated notifications (newest first) |
| `PATCH` | `/api/notifications/read-all` | Mark all as read |

---

### Stripe Webhook — `/api/stripe/webhook`

Not for direct client use. Verified via `Stripe-Signature` header.

| Stripe Event | Action |
|-------------|--------|
| `payment_intent.succeeded` | Mark `SUCCEEDED`, trigger wallet deposit |
| `payment_intent.payment_failed` | Mark `FAILED`, record reason |
| `payment_intent.canceled` | Mark `CANCELED` |
| `charge.refunded` | Mark `REFUNDED` |

---

## 📨 Event-Driven Architecture

### Kafka Topics

| Topic | Producer | Consumer | Payload |
|-------|----------|----------|---------|
| `bid.events` | `BidEventProducer` | `BidEventConsumer` | `BidEvent` |
| `auction-ended` | `AuctionEndedProducer` | `AuctionEndedConsumer` | `AuctionEndedEvent` |
| `payment.events` | `PaymentLifecycleProducer` | `PaymentLifecycleConsumer` | `PaymentLifecycleEvent` |

### RabbitMQ Queues

| Queue | Exchange | Routing Key | Purpose |
|-------|----------|------------|---------|
| `notification.queue` | `notification.exchange` | `notification.send` | In-app notifications |
| `email.queue` | `email.exchange` | `email.send` | Outbound emails |

### Bid Placement Flow

```
POST /api/bids
  └─► BidService
        ├─ Validate amount > current highest bid
        ├─ Freeze wallet funds (optimistic lock)
        ├─ Release previous highest bidder's frozen funds
        ├─ Persist Bid entity
        └─► BidEventProducer → Kafka: bid.events
                                      │
                              BidEventConsumer
                                      │
                              RabbitMQ: notification.queue
```

### Auction End Flow

```
AuctionSchedulerService (cron: every 1 min)
  ├─ Find items WHERE endDate <= NOW AND status = ACTIVE
  ├─ Batch update → PROCESSING
  └─► AuctionEndedProducer → Kafka: auction-ended
                                    │
                           AuctionEndedConsumer
                                    │
                    AuctionEndedOrchestratorService
                      ├─ AuctionWinnerService: find highest bid
                      ├─ Winning bid → WON
                      ├─ All other bids → LOST
                      ├─ Release losers' frozen funds
                      ├─ Create AuctionWinner record
                      ├─ Item → SOLD / EXPIRED
                      └─► RabbitMQ: winner notification
```

---

## 🗄 Data Models

<details>
<summary><b>Entity Relationship Overview</b></summary>

```
USER ──────────────────────────────────────────────────────────────
  │ 1:1 WALLET          │ 1:N BIDS          │ 1:N PAYMENTS
  │                     │                   │
  ▼                     ▼                   ▼
WALLET               BID ──1:1──► AUCTION_WINNER
  availableBalance     itemId
  frozenBalance        bidAmount
  totalSpend           status [ACTIVE|WON|LOST|CANCELLED]
  walletType           version (optimistic lock)
  version
                    ITEM ──────────────────────────────────────────
                      │ 1:N ITEM_IMAGES
                      │ 1:N BIDS
                      category [13 types]
                      status [PENDING|ACTIVE|PROCESSING|SOLD|EXPIRED|CANCELLED]
                      startingPrice / currentHighestBid
                      startDate / endDate
                      version (optimistic lock)

USER ──1:N──► NOTIFICATIONS
                message
                isRead
                createdAt
```

</details>

<details>
<summary><b>Enumerations</b></summary>

| Enum | Values |
|------|--------|
| **Item Category** | `ELECTRONICS` `FURNITURE` `CLOTHING` `BOOKS` `ART` `VEHICLES` `REAL_ESTATE` `JEWELRY` `COLLECTIBLES` `SPORTS` `HOME_AND_GARDEN` `TOYS` `OTHER` |
| **Item Status** | `PENDING` → `ACTIVE` → `PROCESSING` → `SOLD` / `EXPIRED` / `CANCELLED` |
| **Bid Status** | `ACTIVE` → `WON` / `LOST` / `CANCELLED` |
| **Wallet Type** | `BIDDER` `SELLER` |
| **Payment Status** | `INITIATED` → `PROCESSING` → `SUCCEEDED` / `FAILED` / `CANCELED` / `REFUNDED` |
| **User Role** | `USER` `POSTER` `ADMIN` |
| **User Status** | `active` `blocked` |

</details>

---

## 🔒 Security

### Authentication Flow

```
1. POST /api/auth/login
       │
       ▼
   AuthService validates credentials (BCrypt)
       │
       ▼
   JwtUtil generates:
     • accessToken  ──── 15 min TTL
     • refreshToken ──── 7 day TTL
       │
       ▼
   HTTP-only Secure cookies set on response

2. Every subsequent request:
       │
       ▼
   JwtAuthenticationFilter
     ├─ Reads cookie
     ├─ Validates signature & expiry
     ├─ Checks Redis blacklist
     └─ Loads SecurityContext

3. POST /api/auth/logout
     ├─ Blacklist both tokens in Redis (TTL = remaining token life)
     └─ Clear cookies
```

### Cookie Attributes

| Cookie | HttpOnly | Secure | SameSite | Max-Age |
|--------|----------|--------|----------|---------|
| `accessToken` | ✅ | Configurable | Lax | 900s |
| `refreshToken` | ✅ | Configurable | Lax | 604800s |

### Public Endpoints (no auth required)

```
POST  /api/auth/register
POST  /api/auth/login
GET   /api/auth/check-username/**
GET   /api/auth/check-email/**
POST  /api/items/search
GET   /api/items/top-viewed
GET   /api/items/*/detail
GET   /api/bids/item/*/highest
GET   /api/bids/item/*/winner
GET   /api/bids/item/**
POST  /api/stripe/webhook
GET   /actuator/health
```

---

## ⚡ Rate Limiting

Implemented via **Bucket4j** (`RateLimitFilter`) — applied before JWT validation.

| Scope | Limit | Window |
|-------|-------|--------|
| Auth endpoints (`/api/auth/**`) | 10 requests | 30 minutes |
| All other API endpoints | 100 requests | 30 minutes |

On breach: `429 Too Many Requests` with `X-RateLimit-Remaining: 0` and `X-RateLimit-Reset` headers.

Nginx adds a second layer: `10 req/s` with burst of `20`.

---

## 🐳 Infrastructure

### Docker Compose Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `postgres` | postgres:16-alpine | 5432 | Primary database |
| `pgadmin` | dpage/pgadmin4 | 5050 | DB management UI |
| `redis` | redis:7-alpine | 6379 | Cache, tokens, leaderboards |
| `kafka` | apache/kafka:3.8.0 | 9092 | Event streaming (KRaft — no Zookeeper) |
| `rabbitmq` | rabbitmq:3.13-management | 5672 / 15672 | Message queue + management UI |
| `app` | Dockerfile | 8080 | Spring Boot application |

### Redis Key Patterns

| Key | Structure | Purpose |
|-----|-----------|---------|
| `blacklist:{token}` | String + TTL | Invalidated JWT tokens |
| `active:{userId}` | String | Active session tracking |
| `top:viewed:items` | Sorted Set (score = views) | Item view leaderboard |

### Nginx Configuration Summary

| Path | Behaviour |
|------|-----------|
| `/ws` | WebSocket proxy, 86400s timeout, no rate limit |
| `/api/` | Rate limited: 10 req/s, burst 20 |
| `/actuator/health` | Health check, no rate limit |
| `/` | Default proxy to backend |

---

## 🧪 Testing

```bash
# Run all tests
./mvnw test

# With coverage report
./mvnw test jacoco:report

# Single class
./mvnw test -Dtest=BidServiceTest
```

### Test Suite

| Test Class | What It Covers |
|-----------|---------------|
| `AuthServiceTest` | Registration, login, token refresh |
| `BidServiceTest` | Bid placement, fund freezing, concurrent bids |
| `WalletServiceTest` | Deposits, balance management |
| `AuctionWinnerServiceTest` | Winner determination logic |
| `ItemBidderServiceTest` | Search, pagination, top-viewed |
| `AuctionEndedConsumerTest` | Kafka auction-end event processing |
| `JwtUtilTest` | Token generation, validation, expiry |
| `RateLimitFilterTest` | IP throttling, limit enforcement |

---

## 🤝 Contributing

1. **Fork** the repo
2. **Branch**: `git checkout -b feature/your-feature`
3. **Write tests** for your changes
4. **Verify**: `./mvnw test`
5. **Commit** using [Conventional Commits](https://www.conventionalcommits.org/):
   ```
   feat:     new feature
   fix:      bug fix
   docs:     documentation only
   test:     tests only
   refactor: no feature/fix change
   chore:    build / tooling
   ```
6. **Push** and open a Pull Request

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=6,11,20&height=100&section=footer" width="100%"/>

<sub>Built with ❤️ · Spring Boot · Kafka · Redis · Stripe</sub>

</div>
