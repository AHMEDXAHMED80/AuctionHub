# AuctionHub

A scalable real-time auction platform built with **Spring Boot 4** and **Java 21**, featuring dual message broker architecture (Kafka + RabbitMQ), Stripe payment integration, and Redis-powered caching.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Configuration](#configuration)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Infrastructure Services](#infrastructure-services)
- [Security](#security)
- [Roadmap](#roadmap)
- [License](#license)

## Overview

AuctionHub is a production-grade backend platform that enables users to list items for auction, place real-time bids, and manage payments through an integrated wallet system. The platform leverages a **dual message broker architecture** — Apache Kafka for high-throughput bid event streaming and RabbitMQ for reliable notification delivery — alongside Redis for caching, token management, and real-time leaderboards.

## Features

### Authentication & Authorization
- JWT-based stateless authentication with access and refresh token rotation
- Secure HTTP-only cookie storage for tokens
- Token blacklisting via Redis (instant revocation on logout)
- Active token tracking with Redis for session management
- Role-based access control (USER, POSTER, ADMIN)
- BCrypt password encryption

### Auction Management
- Create and manage auction listings with multiple images
- Support for 13 item categories (Electronics, Furniture, Art, Vehicles, etc.)
- Full auction lifecycle management (PENDING → ACTIVE → PROCESSING → SOLD/EXPIRED/CANCELLED)
- Automated auction expiration via scheduled cron job with batch processing
- Automatic winner determination through Kafka event pipeline
- Image ordering and swapping capabilities

### Bidding System
- Real-time bid placement with Kafka event streaming
- Wallet balance verification and fund freezing before bid placement
- Bid status tracking (ACTIVE, WON, LOST, CANCELLED)
- Bid history per item and per user
- Active/inactive bid filtering
- Real-time winning status checks

### Payment & Wallet System
- Stripe integration for secure payment processing
- Asynchronous webhook handling for payment confirmation
- User wallet with available and frozen balance tracking
- Fund freezing on bid placement, release on loss
- Payment history with full lifecycle (PENDING → COMPLETED/FAILED/REFUNDED)
- Support for top-ups up to $10,000 per transaction

### Event-Driven Architecture
- **Apache Kafka** for bid event streaming (producer → consumer pipeline)
- **Kafka** for auction-ended events triggering winner determination
- **RabbitMQ** for notification delivery (email, in-app)
- **WebSocket** support for real-time bid updates to connected clients

### Real-Time Features
- Redis Sorted Sets (`ZSET`) for tracking top viewed items leaderboard
- WebSocket integration for live bid notifications
- Top 5 most viewed items endpoint with ranking

### Security & Rate Limiting
- CSRF protection with cookie-based tokens (smart bypass for Bearer auth & webhooks)
- IP-based rate limiting using Bucket4j
  - Auth endpoints: 10 requests per 30 minutes
  - General endpoints: 100 requests per 30 minutes
- CORS configuration for frontend integration
- Global exception handling with consistent error responses

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| Database | PostgreSQL 15 |
| Caching & Sessions | Redis 7 |
| Message Queue | RabbitMQ 3 |
| Event Streaming | Apache Kafka |
| Payments | Stripe API |
| Authentication | JWT (jjwt 0.12.6) |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Validation |
| Object Mapping | MapStruct 1.5.5 |
| Rate Limiting | Bucket4j 8.16 |
| Reverse Proxy | Nginx |
| Build Tool | Maven |
| Containerization | Docker Compose |
| Monitoring | Spring Actuator |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Applications                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                    (HTTPS)
                                       │
                                       ▼
                              ┌─────────────────┐
                              │      Nginx       │
                              │  (Reverse Proxy) │
                              └────────┬────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                            │
│                                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │  Auth    │ │  Item    │ │   Bid    │ │ Wallet   │ │ Payment  │          │
│  │Controller│ │Controller│ │Controller│ │Controller│ │Controller│          │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘          │
│       │             │            │             │            │                │
│  ┌────▼─────────────▼────────────▼─────────────▼────────────▼──────────────┐│
│  │                        Service Layer                                     ││
│  │  AuthService │ ItemService │ BidService │ WalletService │ PaymentService ││
│  │              │ AuctionScheduler │ AuctionWinnerService │ ItemImageService││
│  └────────┬─────────────────────────────────────────────────┬──────────────┘│
│           │                                                 │               │
│  ┌────────▼─────────────────────────────────────────────────▼──────────────┐│
│  │                       Repository Layer (JPA)                             ││
│  └──────────────────────────────────────────────────────────────────────────┘│
└─────────┬───────────────────────────────────────────────────────────────────┘
          │                    │                    │                │
          ▼                    ▼                    ▼                ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐
│  PostgreSQL  │  │      Redis       │  │   RabbitMQ   │  │     Kafka        │
│  (Database)  │  │ (Cache/Tokens/   │  │(Notifications│  │ (Bid Events /    │
│              │  │  Leaderboards)   │  │  & Emails)   │  │  Auction Events) │
└──────────────┘  └──────────────────┘  └──────────────┘  └──────────────────┘
```

### Event Flow: Bid Placement
```
User places bid → BidController → BidService (validates + freezes funds)
    → KafkaProducer (BidEvent) → KafkaConsumer → Updates bid record
    → RabbitMQ Publisher → NotificationListener → Stores notification
```

### Event Flow: Auction Expiration
```
Cron Job (every minute) → AuctionSchedulerService
    → Finds expired auctions → Batch updates to PROCESSING status
    → KafkaProducer (AuctionEndedEvent) → AuctionEndedConsumer
    → AuctionWinnerService → Determines winner, updates statuses, handles funds
```

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Docker and Docker Compose
- Stripe account (for payment processing)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/auctionhub.git
   cd auctionhub
   ```

2. **Start infrastructure services**
   ```bash
   docker compose up -d
   ```

   This starts:
   - PostgreSQL (port 5432)
   - Redis (port 6379)
   - RabbitMQ (ports 5672, 15672 for management UI)
   - Kafka with Zookeeper (port 9092)
   - Kafka UI (port 8080)

3. **Build the application**
   ```bash
   ./mvnw clean install
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

   The application starts on `http://localhost:8080`

### Configuration

Create or update `src/main/resources/application.properties`:

```properties
# Application
spring.application.name=auctionhub

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/auctionhub
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT (Generate a secure Base64 key for production)
JWT_SECRET_B64=your_base64_encoded_secret_key

# Stripe
STRIPE_SECRET_KEY=sk_test_your_stripe_secret_key
STRIPE.WEBHOOK.SECRET=whsec_your_webhook_secret

# Cookie Security (set to true in production with HTTPS)
auth.cookie.secure=false
auth.cookie.sameSite=Lax
```

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Authenticate user |
| POST | `/api/auth/logout` | Invalidate tokens |
| POST | `/api/auth/refresh` | Refresh access token |
| GET | `/api/auth/check-username/{username}` | Check username availability |
| GET | `/api/auth/check-email/{email}` | Check email availability |

### Items (Seller)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/items` | Create new item listing |
| GET | `/api/items/user` | Get current seller's items |
| PUT | `/api/items/{itemId}` | Edit item details |
| PUT | `/api/items/{itemId}/images/{imageId}` | Replace item image |
| DELETE | `/api/items/{itemId}/images/{imageId}` | Remove item image |
| POST | `/api/items/{itemId}/images/swap` | Swap image positions |

### Bids

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/bids` | Place a bid on an item |
| GET | `/api/bids/item/{itemId}` | Get bid history for an item |
| GET | `/api/bids/my-bids` | Get all bids by current user |
| GET | `/api/bids/item/{itemId}/highest` | Get current highest bid |
| GET | `/api/bids/item/{itemId}/am-i-winning` | Check if user is winning |
| GET | `/api/bids/item/{itemId}/winner` | Get current highest bidder username |
| GET | `/api/bids/active` | Get user's active bids |
| GET | `/api/bids/history` | Get user's ended auction bids |

### Wallet

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/wallet` | Get wallet info |
| POST | `/api/wallet` | Create wallet |
| POST | `/api/wallet/deposit` | Add balance to wallet |
| GET | `/api/wallet/balance` | Get balance summary (available + frozen) |

### Payments

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/payments` | Create payment (wallet top-up) |
| GET | `/api/payments/history` | Get payment history |
| GET | `/api/payments/{paymentId}` | Get payment details |
| POST | `/api/payments/{paymentId}/cancel` | Cancel pending payment |
| POST | `/api/payments/{paymentId}/refund` | Refund successful payment |

### Webhooks

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/stripe/webhook` | Stripe webhook handler |

## Project Structure

```
src/main/java/com/example/auctionhub/auctionhub/
├── config/                     # Application configuration
│   ├── KafkaProducers/         # Kafka producer configs
│   ├── RabbitMQConfig.java     # RabbitMQ queues and exchanges
│   ├── SecurityConfig.java     # Spring Security configuration
│   ├── WebConfig.java          # Web configuration
│   └── WebSocketConfig.java    # WebSocket configuration
├── constants/                  # API constants and endpoints
├── controller/                 # REST controllers
│   ├── AuthController.java
│   ├── BidController.java
│   ├── ItemController.java
│   ├── PaymentController.java
│   ├── StripeWebhookController.java
│   └── WalletController.java
├── dto/                        # Data Transfer Objects
├── events/                     # Event handling
│   ├── consumer/               # Kafka consumers
│   ├── dto/                    # Event DTOs
│   └── producer/               # Kafka producers
├── mapper/                     # MapStruct mappers
├── messaging/                  # RabbitMQ messaging
│   ├── listener/               # Message listeners
│   └── publisher/              # Message publishers
├── models/                     # JPA entities
│   ├── AuctionWinner.java
│   ├── Bid.java
│   ├── Item.java
│   ├── ItemImages.java
│   ├── Notifications.java
│   ├── Payment.java
│   ├── User.java
│   └── Wallet.java
├── ratelimiter/                # Rate limiting
├── redis/                      # Redis services
│   ├── TokenActiveList.java
│   ├── TokenBlackListService.java
│   └── TrackTopViewedItems.java
├── repository/                 # Spring Data repositories
├── security/                   # Security utilities
│   ├── JwtAuthenticationFilter.java
│   ├── JwtUtil.java
│   └── SecurityUtils.java
└── service/                    # Business logic services
    ├── AuthService.java
    ├── AuctionSchedulerService.java
    ├── AuctionWinnerService.java
    ├── BidService.java
    ├── ItemBidderService.java
    ├── ItemImageService.java
    ├── ItemsSellerService.java
    ├── PaymentService.java
    ├── StripeService.java
    └── WalletService.java
```

## Infrastructure Services

### Docker Compose Services

| Service | Port | Description |
|---------|------|-------------|
| Nginx | 80 | Reverse proxy with WebSocket support |
| Spring Boot App | 8080 | Application server |
| PostgreSQL | 5432 | Primary database |
| Redis | 6379 | Token caching, session management, leaderboards |
| RabbitMQ | 5672, 15672 | Message queue (15672 for management UI) |
| Zookeeper | 2181 | Kafka coordination |
| Kafka | 9092 | Event streaming |

### Accessing Management UIs

- **RabbitMQ Management**: http://localhost:15672 (user: myuser, password: secret)
- **Kafka UI**: http://localhost:8080

## Security

### Authentication Flow

1. User registers/logs in via `/api/auth/register` or `/api/auth/login`
2. Server issues JWT access token (15 min) and refresh token (7 days)
3. Tokens are stored in HTTP-only, secure cookies
4. Access token is validated on each request via `JwtAuthenticationFilter`
5. Refresh tokens can be rotated via `/api/auth/refresh`
6. Logout blacklists tokens in Redis

### Rate Limiting

- **Authentication endpoints**: 10 requests per 30 minutes per IP
- **General API**: 100 requests per 30 minutes per IP
- Rate limit headers indicate retry time when exceeded

### CSRF Protection

- CSRF tokens required for state-changing operations from browser clients
- Bypassed for API clients using Bearer token authentication
- Bypassed for Stripe webhooks

## Roadmap

- [x] JWT authentication with token rotation and Redis blacklisting
- [x] Bid placement with Kafka event streaming
- [x] Wallet system with Stripe payment integration
- [x] Automated auction expiration and winner determination
- [x] RabbitMQ notification pipeline
- [x] Redis-powered top viewed items leaderboard
- [x] IP-based rate limiting with Bucket4j
- [x] Docker Compose deployment with Nginx reverse proxy
- [ ] Public item browsing and search endpoints
- [ ] Elasticsearch full-text search integration
- [ ] Email verification and password reset
- [ ] Auto-bidding system
- [ ] Admin dashboard endpoints
- [ ] Comprehensive test coverage (unit + integration)
- [ ] API documentation with OpenAPI/Swagger
- [ ] CI/CD pipeline with GitHub Actions

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Built with Spring Boot 4 and Java 21
