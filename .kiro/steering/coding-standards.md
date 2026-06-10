# GOLA Backend Coding Standards

## Architecture Principles

### Modular Monolith Pattern
- Organize code by business domain (trip, user, payment, safety, etc.)
- Keep clear boundaries between modules
- Each module should be independently testable
- Design for potential future extraction into microservices

### Layered Architecture
- **Controllers**: Thin layer for HTTP handling, validation, and response formatting
- **Services**: Fat services containing all business logic and transactions
- **Repositories**: Data access layer using Spring Data JPA
- **DTOs**: Separate request/response objects from entities
- **Mappers**: Use MapStruct for entity-DTO conversions

## Code Organization

### Package Structure
```
com.gola
├── config/          # Spring configuration classes
├── controller/      # REST endpoints
├── dto/             # Request/Response objects (organized by domain)
├── entity/          # JPA entities
├── exception/       # Custom exceptions and handlers
├── mapper/          # MapStruct interfaces
├── repository/      # Spring Data repositories
├── security/        # Security filters, JWT utilities
├── service/         # Business logic
└── validator/       # Custom validators
```

### Naming Conventions
- **Controllers**: `{Domain}Controller` (e.g., `TripController`)
- **Services**: `{Domain}Service` (e.g., `TripService`)
- **Repositories**: `{Entity}Repository` (e.g., `TripRepository`)
- **DTOs**: `{Action}{Domain}Request/Response` (e.g., `CreateTripRequest`, `TripResponse`)
- **Entities**: Singular noun (e.g., `Trip`, `User`, `TripStop`)
- **Mappers**: `{Entity}Mapper` (e.g., `TripMapper`)

## Java & Spring Boot Standards

### Java Version
- Use **Java 21** features where appropriate
- Leverage records for immutable DTOs
- Use pattern matching and switch expressions
- Prefer `var` for local variables when type is obvious

### Lombok Usage
- Use `@RequiredArgsConstructor` for dependency injection
- Use `@Slf4j` for logging
- Use `@Builder` for entities and complex DTOs
- Use `@Data` or `@Getter/@Setter` for DTOs
- Avoid `@AllArgsConstructor` and `@NoArgsConstructor` unless needed for JPA

### Spring Annotations
- Use `@RestController` for REST endpoints
- Use `@Service` for business logic
- Use `@Repository` for data access (usually not needed with Spring Data)
- Use `@Transactional` on service methods that modify data
- Use `@Valid` for request validation
- Use `@RequiredArgsConstructor` for constructor injection (preferred over field injection)

## API Design

### REST Endpoints
- Use plural nouns for resources: `/trips`, `/users`, `/posts`
- Use HTTP methods correctly:
  - `POST` for creation
  - `GET` for retrieval
  - `PATCH` for partial updates
  - `DELETE` for deletion
- Use path parameters for resource IDs: `/trips/{id}`
- Use query parameters for filtering/pagination: `?page=0&size=20`

### Response Format
Always wrap responses in `ApiResponse<T>`:
```java
@PostMapping
public ResponseEntity<ApiResponse<TripResponse>> createTrip(@Valid @RequestBody CreateTripRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok("Trip created", tripService.createTrip(SecurityUtils.getCurrentUserId(), req)));
}
```

### Pagination
Use `PageResponse<T>` for paginated results:
```java
public PageResponse<TripResponse> listMyTrips(UUID userId, Pageable pageable) {
    return new PageResponse<>(tripRepo.findAllForUser(userId, pageable).map(this::mapToResponse));
}
```

### OpenAPI Documentation
- Add `@Tag` to controllers for grouping
- Add `@Operation` to endpoints with clear summaries
- Document request/response schemas with examples

## Security

### Authentication
- Use JWT tokens for authentication
- Extract current user with `SecurityUtils.getCurrentUserId()`
- Never trust user IDs from request body; always use authenticated user

### Authorization
- Check ownership/membership before allowing operations
- Use role-based checks (OWNER, EDITOR, VIEWER)
- Throw `GolaException.forbidden()` for unauthorized access

### Input Validation
- Use Jakarta Validation annotations on DTOs
- Validate all user inputs
- Sanitize data before storage

## Database & JPA

### Entity Design
- Use `UUID` for primary keys
- Include audit fields: `createdAt`, `updatedAt`, `deletedAt`
- Use soft deletes (set `deletedAt` instead of physical deletion)
- Use enums for status fields
- Use `@Builder` for entity construction

### Relationships
- Use `@ManyToOne`, `@OneToMany` appropriately
- Avoid bidirectional relationships unless necessary
- Use `fetch = FetchType.LAZY` by default
- Use `@JoinColumn` to specify foreign key names

### Queries
- Use Spring Data JPA query methods when possible
- Use `@Query` for complex queries
- Use native queries only when JPQL is insufficient
- Always filter out soft-deleted records: `WHERE deletedAt IS NULL`

### Transactions
- Mark service methods with `@Transactional` when they modify data
- Keep transactions as short as possible
- Don't call external APIs inside transactions

## Error Handling

### Custom Exceptions
Use `GolaException` factory methods:
```java
throw GolaException.notFound("Trip");
throw GolaException.forbidden();
throw GolaException.conflict("Trip session already active");
throw GolaException.badRequest("Invalid date range");
```

### Global Exception Handler
- All exceptions are handled by `@RestControllerAdvice`
- Return consistent error responses
- Log errors appropriately

## Logging

### Log Levels
- `ERROR`: System errors, exceptions
- `WARN`: Recoverable issues, deprecated usage
- `INFO`: Important business events (trip created, payment processed)
- `DEBUG`: Detailed flow information
- `TRACE`: Very detailed debugging

### Log Format
```java
log.info("Trip created: {} by user: {}", trip.getId(), userId);
log.error("Failed to process payment for order: {}", orderId, exception);
```

## Testing

### Test Structure
- Unit tests for services (mock repositories)
- Integration tests for controllers (use TestContainers)
- Use `@SpringBootTest` for integration tests
- Use `@WebMvcTest` for controller tests

### Test Naming
- Method name: `should{ExpectedBehavior}_when{Condition}`
- Example: `shouldCreateTrip_whenValidRequest()`

## Performance

### Caching
- Use Redis for frequently accessed data
- Cache external API responses (Google Places, etc.)
- Set appropriate TTLs

### Async Processing
- Use `@Async` for fire-and-forget operations
- Send emails and push notifications asynchronously
- Don't block request threads

### Database Optimization
- Use indexes on frequently queried columns
- Use pagination for large result sets
- Avoid N+1 queries (use JOIN FETCH)
- Use database-level constraints

## Code Quality

### General Principles
- Keep methods small and focused (< 20 lines ideally)
- Use meaningful variable names
- Avoid magic numbers; use constants or enums
- Don't repeat yourself (DRY)
- Write self-documenting code

### Comments
- Write comments for "why", not "what"
- Document complex business logic
- Keep comments up-to-date with code changes

### Code Review Checklist
- [ ] Follows naming conventions
- [ ] Proper error handling
- [ ] Security checks in place
- [ ] Transactions used correctly
- [ ] No sensitive data in logs
- [ ] DTOs used instead of entities in responses
- [ ] Validation annotations present
- [ ] OpenAPI documentation added
