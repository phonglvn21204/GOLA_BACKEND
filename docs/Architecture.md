# GOLA Backend Architecture

## Overview
GOLA backend is built using a **Modular Monolith** architecture on **Java 21** and **Spring Boot 3**.
This architecture provides the simplicity and deployment ease of a monolith while keeping the internal boundaries clean to allow easy extraction into microservices if scaling requires it.

## Tech Stack
- **Framework**: Spring Boot 3 + Java 21
- **Database**: PostgreSQL 16 (via Amazon RDS or Supabase) with PostGIS extension for geo-queries
- **Migrations**: Flyway
- **Caching & Rate Limiting**: Redis
- **Security**: Spring Security + JWT
- **Real-time**: Spring WebSocket (STOMP) over WebSocket for chat, live location, SOS
- **AI**: Google Gemini Flash Lite (via Spring integration or direct API)
- **Payments**: Stripe API
- **APIs**: Springdoc OpenAPI / Swagger

## Directory Structure
\\\
src/main/java/com/gola
â”œâ”€â”€ admin           # Admin specific tools and metric aggregations
â”œâ”€â”€ ai              # Integration with Gemini for Itinerary and Albums
â”œâ”€â”€ auth            # Login, Registration, JWT management
â”œâ”€â”€ common          # Base entities, Exceptions, Global handlers
â”œâ”€â”€ community       # Feed, Posts, Stories, Reactions
â”œâ”€â”€ config          # Application settings (Redis, Security, WebSocket)
â”œâ”€â”€ map             # Google places integration, caching locations
â”œâ”€â”€ notification    # Push notifications (FCM), Email (Resend)
â”œâ”€â”€ payment         # Stripe integrations, orders, subscriptions
â”œâ”€â”€ quest           # Gamification tasks, badges, coin rewards
â”œâ”€â”€ safety          # SOS logic, incidents, live tracking
â”œâ”€â”€ security        # JWT filters, password encodings
â”œâ”€â”€ trip            # Core trips, members, routing
â””â”€â”€ user            # User profiles, wallets, preferences
\\\

## Key Patterns
- **Fat Services, Thin Controllers**: Controllers only handle HTTP parsing/validation, services handle transactions and business logic.
- **DTO Mappings**: Uses MapStruct to convert between request/response DTOs and internal Entities.
- **Async Processing**: Fire-and-forget operations like sending emails and push notifications use @Async and Redis-backed Message Queues if needed.
- **WebSocket Broadcasting**: For live-trip locations (/topic/trip/{id}) and SOS alerts (/topic/admin/sos).