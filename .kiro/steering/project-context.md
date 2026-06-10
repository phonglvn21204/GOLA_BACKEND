# GOLA Backend Project Context

## Project Overview
GOLA is a travel companion application that combines trip planning, social features, safety tools, and gamification. The backend is built as a modular monolith using Spring Boot 3 and Java 21.

## Technology Stack

### Core Framework
- **Java 21**: Latest LTS with modern language features
- **Spring Boot 3.3.2**: Main application framework
- **Gradle**: Build tool and dependency management

### Database & Persistence
- **PostgreSQL 16**: Primary database with PostGIS extension
- **Flyway**: Database migrations
- **Spring Data JPA**: ORM and repository layer
- **Redis**: Caching and rate limiting

### Security
- **Spring Security**: Authentication and authorization
- **JWT (jjwt 0.12.5)**: Token-based authentication
- **Passay**: Password validation

### API & Documentation
- **Springdoc OpenAPI 2.5.0**: API documentation and Swagger UI
- **MapStruct 1.5.5**: DTO-Entity mapping

### External Integrations
- **Stripe 24.18.0**: Payment processing
- **Twilio 9.14.1**: SMS notifications
- **Google Gemini**: AI-powered trip planning and album generation
- **Google Places API**: Location data and search

### Real-time Communication
- **Spring WebSocket**: Live location tracking, chat, SOS alerts
- **STOMP**: Messaging protocol over WebSocket

### Utilities
- **Lombok**: Boilerplate reduction
- **Bucket4j**: Rate limiting
- **Apache Commons Lang3**: Utility functions
- **Guava**: Collections and utilities
- **Jackson**: JSON processing

## Domain Modules

### 1. Authentication & User Management
- User registration and login
- JWT token generation and validation
- Password reset and change
- Email verification
- User profiles and preferences

### 2. Trip Management
- Trip CRUD operations
- Trip stops and itinerary
- Trip members and roles (Owner, Editor, Viewer)
- Trip sharing with tokens
- Live trip sessions
- Trip status tracking (Draft, Active, Completed)

### 3. Social/Community Features
- User posts and stories
- Photo albums
- Comments and reactions
- User following system
- Activity feed
- Content moderation and reporting

### 4. Safety Features
- SOS alerts with location
- Emergency contacts management
- Incident reporting
- Live location tracking during trips
- Safety notifications

### 5. Gamification (Quest System)
- Badges and achievements
- Coin rewards
- Quest completion tracking
- User wallet and points

### 6. Payment & Billing
- Stripe integration
- Subscription management
- One-time purchases
- Pricing tiers
- Webhook handling for payment events

### 7. AI Features
- AI-generated trip itineraries (Gemini)
- Smart album creation from photos
- Travel recommendations
- Content suggestions

### 8. Notifications
- Push notifications (FCM)
- Email notifications
- SMS notifications (Twilio)
- Notification preferences
- Real-time WebSocket notifications

### 9. Places & Maps
- Google Places integration
- Location search and autocomplete
- Place details caching
- Geospatial queries (PostGIS)

### 10. Admin Tools
- User management
- Content moderation
- System metrics and analytics
- SOS alert monitoring

## Key Features

### Real-time Capabilities
- Live location sharing during active trips
- WebSocket-based chat
- Real-time SOS alerts to admin dashboard
- Live trip updates to members

### Security Features
- JWT-based authentication
- Role-based access control
- Rate limiting per user/IP
- Input validation and sanitization
- Secure password policies

### Performance Optimizations
- Redis caching for frequently accessed data
- Async processing for emails and notifications
- Database query optimization
- Connection pooling
- Pagination for large datasets

## Environment Configuration

### Profiles
- `dev`: Local development
- `test`: Testing environment
- `prod`: Production environment

### Key Configuration Areas
- Database connection (PostgreSQL)
- Redis connection
- JWT secret and expiration
- Stripe API keys
- Twilio credentials
- Google API keys (Places, Gemini)
- Email service configuration
- WebSocket configuration
- CORS settings

## Build & Deployment

### Build Commands
```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application
./gradlew bootRun

# Create executable JAR
./gradlew bootJar
```

### Docker Support
- Dockerfile available in `docker/` directory
- Docker Compose for local development with PostgreSQL and Redis

### Deployment
- Heroku-ready with Procfile
- Can be deployed to any platform supporting Java 21
- Requires PostgreSQL 16+ and Redis

## Database Schema

### Core Tables
- `users`: User accounts and profiles
- `trips`: Trip information
- `trip_stops`: Itinerary stops
- `trip_members`: Trip participants and roles
- `trip_sessions`: Live trip tracking
- `posts`: Social media posts
- `comments`: Post comments
- `reactions`: Post reactions
- `badges`: Achievement definitions
- `user_badges`: User achievements
- `notifications`: User notifications
- `emergency_contacts`: Safety contacts
- `sos_alerts`: Emergency alerts
- `payments`: Payment records
- `subscriptions`: User subscriptions

### Audit Fields
All entities include:
- `id`: UUID primary key
- `createdAt`: Timestamp
- `updatedAt`: Timestamp
- `deletedAt`: Soft delete timestamp (nullable)

## API Structure

### Base URL
- Development: `http://localhost:8080`
- Production: Configured via environment

### Main Endpoints
- `/auth/*`: Authentication endpoints
- `/users/*`: User management
- `/trips/*`: Trip operations
- `/posts/*`: Social features
- `/notifications/*`: Notification management
- `/payments/*`: Payment processing
- `/admin/*`: Admin operations
- `/ai/*`: AI-powered features
- `/places/*`: Location services

### WebSocket Endpoints
- `/ws`: WebSocket connection
- `/topic/trip/{tripId}`: Trip-specific updates
- `/topic/admin/sos`: SOS alerts for admins
- `/user/queue/notifications`: User-specific notifications

## Development Workflow

### Adding a New Feature
1. Create/update entity in `entity/` package
2. Create repository interface in `repository/`
3. Create DTOs in `dto/{domain}/`
4. Create mapper interface in `mapper/`
5. Implement service in `service/`
6. Create controller in `controller/`
7. Add OpenAPI documentation
8. Write tests
9. Create Flyway migration if needed

### Database Migrations
- Located in `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql`
- Never modify existing migrations
- Always test migrations on a copy of production data

## Common Patterns

### Getting Current User
```java
UUID userId = SecurityUtils.getCurrentUserId();
```

### Authorization Check
```java
if (!trip.getOwnerId().equals(userId)) {
    throw GolaException.forbidden();
}
```

### Soft Delete
```java
entity.setDeletedAt(Instant.now());
repository.save(entity);
```

### Pagination
```java
PageRequest pageable = PageRequest.of(page, size);
Page<Entity> results = repository.findAll(pageable);
return new PageResponse<>(results.map(mapper::toDto));
```

## Troubleshooting

### Common Issues
- **JWT errors**: Check token expiration and secret configuration
- **Database connection**: Verify PostgreSQL is running and credentials are correct
- **Redis connection**: Ensure Redis is running on configured port
- **CORS errors**: Check CORS configuration in SecurityConfig
- **WebSocket issues**: Verify WebSocket configuration and STOMP setup

### Logging
- Application logs: Check console output or log files
- Boot logs: `boot*.log` files in project root
- Enable debug logging: Set `logging.level.com.gola=DEBUG` in application.yml

## Resources

### Documentation
- Architecture: `docs/Architecture.md`
- API Examples: `docs/curl-examples.md`
- Frontend API Map: `docs/frontend-api-map.md`
- Postman Collection: `docs/gola-api.postman_collection.json`
- Roadmap: `docs/Roadmap.md`

### External Documentation
- Spring Boot: https://spring.io/projects/spring-boot
- Spring Security: https://spring.io/projects/spring-security
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- MapStruct: https://mapstruct.org/
- Stripe API: https://stripe.com/docs/api
- Google Places API: https://developers.google.com/maps/documentation/places
