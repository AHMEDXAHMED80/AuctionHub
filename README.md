# AuctionHub

A scalable real-time auction platform built with Spring Boot 4, featuring secure authentication, integrated payments, and event-driven architecture.

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

AuctionHub is a robust backend platform that enables users to list items for auction, place bids, and manage payments through an integrated wallet system. The platform leverages modern event-driven patterns with Kafka and RabbitMQ for real-time bid processing and notifications.

## Features

### Authentication & Authorization
- JWT-based stateless authentication with access and refresh tokens
- Secure HTTP-only cookie storage for tokens
- Token blacklisting and rotation with Redis
- Role-based access control (USER, POSTER, ADMIN)
- BCrypt password encryption

### Auction Management
- Create and manage auction listings with multiple images
- Support for 13 item categories (Electronics, Furniture, Art, Vehicles, etc.)
- Auction lifecycle management (PENDING, ACTIVE, EXPIRED, SOLD, CANCELLED)
- Image ordering and swapping capabilities

### Bidding System
- Real-time bid placement and validation
- Wallet balance verification before bidding
- Bid status tracking (ACTIVE, WON, LOST, CANCELLED)
- Auction winner determination

### Payment & Wallet System
- Stripe integration for secure payment processing
- Webhook handling for asynchronous payment events
- User wallet with available and frozen balance tracking
- Payment history and refund capabilities
- Support for top-ups up to $10,000 per transaction

### Event-Driven Architecture
- Apache Kafka for bid event streaming
- RabbitMQ for notifications and email delivery
- WebSocket support for real-time updates

### Security & Rate Limiting
- CSRF protection with cookie-based tokens
- IP-based rate limiting using Bucket4j
  - Auth endpoints: 10 requests per 30 minutes
  - General endpoints: 100 requests per 30 minutes
- CORS configuration for frontend integration

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| Database | PostgreSQL |
| Caching | Redis |
| Message Queue | RabbitMQ |
| Event Streaming | Apache Kafka |
| Payments | Stripe API |
| Authentication | JWT (jjwt 0.12.6) |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Validation |
| Mapping | MapStruct 1.5.5 |
| Rate Limiting | Bucket4j |
| Build Tool | Maven |
| Containerization | Docker Compose |

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Applications                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   Auth      │  │   Item      │  │  Payment    │  │  Stripe Webhook     │ │
│  │ Controller  │  │ Controller  │  │ Controller  │  │    Controller       │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
│         │                │                │                     │            │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────────▼──────────┐ │
│  │   Auth      │  │   Items     │  │  Payment    │  │     Stripe          │ │
│  │  Service    │  │  Service    │  │  Service    │  │     Service         │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
│         │                │                │                     │            │
│  ┌──────▼────────────────▼────────────────▼─────────────────────▼──────────┐│
│  │                         Repository Layer (JPA)                           ││
│  └──────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
         │                                                    │
         ▼                                                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐
│   PostgreSQL    │  │     Redis       │  │    RabbitMQ     │  │    Kafka    │
│   (Database)    │  │    (Cache)      │  │  (Messaging)    │  │ (Streaming) │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────┘
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

### Items

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/items/user` | Get current user's items |
| POST | `/api/items` | Create new item |
| PUT | `/api/items/{itemId}` | Edit item details |
| PUT | `/api/items/{itemId}/images/{imageId}` | Replace item image |
| DELETE | `/api/items/{itemId}/images/{imageId}` | Remove item image |
| POST | `/api/items/{itemId}/images/swap` | Swap image positions |

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
│   ├── ItemController.java
│   ├── PaymentController.java
│   └── StripeWebhookController.java
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
│   └── TokenBlackListService.java
├── repository/                 # Spring Data repositories
├── security/                   # Security utilities
│   ├── JwtAuthenticationFilter.java
│   ├── JwtUtil.java
│   └── SecurityUtils.java
└── service/                    # Business logic services
```

## Infrastructure Services

### Docker Compose Services

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Primary database |
| Redis | 6379 | Token caching and blacklisting |
| RabbitMQ | 5672, 15672 | Message queue (15672 for management UI) |
| Zookeeper | 2181 | Kafka coordination |
| Kafka | 9092 | Event streaming |
| Kafka UI | 8080 | Kafka management interface |

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

- [ ] Implement `BidController` for bidding API endpoints
- [ ] Add `WalletController` for wallet management
- [ ] Complete WebSocket integration for real-time bid updates
- [ ] Add auction scheduling and automatic expiration
- [ ] Implement email notifications via RabbitMQ
- [ ] Add comprehensive test coverage
- [ ] API documentation with OpenAPI/Swagger

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Built with Spring Boot 4 and Java 21
