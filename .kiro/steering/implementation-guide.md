---
inclusion: auto
---

# GOLA Backend Implementation Guide

## Quick Reference for Common Tasks

### 1. Adding a New REST Endpoint

**Step-by-step process:**

1. **Create DTOs** in `dto/{domain}/`
```java
@Data
@Builder
public class CreateResourceRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    @Size(max = 500, message = "Description too long")
    private String description;
}

@Data
@Builder
public class ResourceResponse {
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
}
```

2. **Create/Update Entity** in `entity/`
```java
@Entity
@Table(name = "resources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resource {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

3. **Create Repository** in `repository/`
```java
@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    @Query("SELECT r FROM Resource r WHERE r.deletedAt IS NULL AND r.id = :id")
    Optional<Resource> findActiveById(@Param("id") UUID id);
    
    @Query("SELECT r FROM Resource r WHERE r.deletedAt IS NULL AND r.userId = :userId")
    Page<Resource> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
}
```

4. **Create Service** in `service/`
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {
    private final ResourceRepository resourceRepo;
    
    @Transactional
    public ResourceResponse create(UUID userId, CreateResourceRequest req) {
        var resource = Resource.builder()
            .userId(userId)
            .name(req.getName())
            .description(req.getDescription())
            .build();
        
        resourceRepo.save(resource);
        log.info("Resource created: {} by user: {}", resource.getId(), userId);
        return mapToResponse(resource);
    }
    
    public PageResponse<ResourceResponse> list(UUID userId, Pageable pageable) {
        return new PageResponse<>(
            resourceRepo.findAllByUserId(userId, pageable)
                .map(this::mapToResponse)
        );
    }
    
    public ResourceResponse get(UUID id, UUID userId) {
        var resource = resourceRepo.findActiveById(id)
            .orElseThrow(() -> GolaException.notFound("Resource"));
        
        if (!resource.getUserId().equals(userId)) {
            throw GolaException.forbidden();
        }
        
        return mapToResponse(resource);
    }
    
    @Transactional
    public void delete(UUID id, UUID userId) {
        var resource = resourceRepo.findActiveById(id)
            .orElseThrow(() -> GolaException.notFound("Resource"));
        
        if (!resource.getUserId().equals(userId)) {
            throw GolaException.forbidden();
        }
        
        resource.setDeletedAt(Instant.now());
        resourceRepo.save(resource);
    }
    
    private ResourceResponse mapToResponse(Resource r) {
        return ResourceResponse.builder()
            .id(r.getId())
            .name(r.getName())
            .description(r.getDescription())
            .createdAt(r.getCreatedAt())
            .build();
    }
}
```

5. **Create Controller** in `controller/`
```java
@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
@Tag(name = "Resources", description = "Resource management")
public class ResourceController {
    private final ResourceService resourceService;
    
    @PostMapping
    @Operation(summary = "Create a new resource")
    public ResponseEntity<ApiResponse<ResourceResponse>> create(
            @Valid @RequestBody CreateResourceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Resource created", 
                resourceService.create(SecurityUtils.getCurrentUserId(), req)));
    }
    
    @GetMapping
    @Operation(summary = "List my resources")
    public ResponseEntity<ApiResponse<PageResponse<ResourceResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
            resourceService.list(SecurityUtils.getCurrentUserId(), 
                PageRequest.of(page, size))));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get resource details")
    public ResponseEntity<ApiResponse<ResourceResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
            resourceService.get(id, SecurityUtils.getCurrentUserId())));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete resource")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        resourceService.delete(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Resource deleted", null));
    }
}
```

6. **Create Flyway Migration** in `src/main/resources/db/migration/`
```sql
-- V{next_version}__create_resources_table.sql
CREATE TABLE resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_resources_user_id ON resources(user_id);
CREATE INDEX idx_resources_deleted_at ON resources(deleted_at);
```

### 2. Adding Validation

Use Jakarta Validation annotations on DTOs:

```java
@Data
public class CreateTripRequest {
    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 100, message = "Title must be 3-100 characters")
    private String title;
    
    @NotNull(message = "Start date is required")
    @Future(message = "Start date must be in the future")
    private Instant startDate;
    
    @Email(message = "Invalid email format")
    private String contactEmail;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number")
    private String phoneNumber;
    
    @Min(value = 1, message = "Duration must be at least 1 day")
    @Max(value = 365, message = "Duration cannot exceed 365 days")
    private Integer durationDays;
}
```

### 3. Working with Relationships

**One-to-Many Example:**
```java
@Entity
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripStop> stops = new ArrayList<>();
    
    public void addStop(TripStop stop) {
        stops.add(stop);
        stop.setTrip(this);
    }
}

@Entity
public class TripStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
}
```

### 4. Async Operations

For fire-and-forget operations like sending emails:

```java
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    @Async
    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to: {}", to);
        // Email sending logic
    }
    
    @Async
    public void sendPushNotification(UUID userId, String message) {
        log.info("Sending push notification to user: {}", userId);
        // Push notification logic
    }
}
```

Enable async in configuration:
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

### 5. Caching with Redis

```java
@Service
@RequiredArgsConstructor
public class PlaceService {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CACHE_KEY_PREFIX = "place:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    
    public PlaceDetails getPlaceDetails(String placeId) {
        String cacheKey = CACHE_KEY_PREFIX + placeId;
        
        // Try cache first
        PlaceDetails cached = (PlaceDetails) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Fetch from API
        PlaceDetails details = fetchFromGooglePlaces(placeId);
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, details, CACHE_TTL);
        
        return details;
    }
}
```

### 6. WebSocket Broadcasting

```java
@Service
@RequiredArgsConstructor
public class LiveLocationService {
    private final SimpMessagingTemplate messagingTemplate;
    
    public void broadcastLocation(UUID tripId, LocationUpdate update) {
        messagingTemplate.convertAndSend(
            "/topic/trip/" + tripId,
            update
        );
    }
    
    public void sendToUser(UUID userId, Notification notification) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            "/queue/notifications",
            notification
        );
    }
}
```

### 7. Rate Limiting

```java
@Service
public class RateLimitService {
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    
    public boolean tryConsume(String key) {
        Bucket bucket = cache.computeIfAbsent(key, k -> createBucket());
        return bucket.tryConsume(1);
    }
    
    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(100)
            .refillGreedy(100, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}
```

### 8. Custom Queries

**JPQL Query:**
```java
@Query("""
    SELECT t FROM Trip t
    JOIN t.members m
    WHERE m.userId = :userId
    AND t.deletedAt IS NULL
    AND t.status = :status
    ORDER BY t.startDate DESC
    """)
Page<Trip> findUserTripsByStatus(
    @Param("userId") UUID userId,
    @Param("status") TripStatus status,
    Pageable pageable
);
```

**Native Query with PostGIS:**
```java
@Query(value = """
    SELECT * FROM places
    WHERE ST_DWithin(
        location::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
        :radiusMeters
    )
    AND deleted_at IS NULL
    ORDER BY ST_Distance(
        location::geography,
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
    )
    LIMIT :limit
    """, nativeQuery = true)
List<Place> findNearby(
    @Param("lat") double latitude,
    @Param("lng") double longitude,
    @Param("radiusMeters") double radiusMeters,
    @Param("limit") int limit
);
```

## Common Patterns

### Authorization Pattern
```java
private Trip getEditableTrip(UUID tripId, UUID userId) {
    var trip = tripRepo.findActiveById(tripId)
        .orElseThrow(() -> GolaException.notFound("Trip"));
    
    if (!memberRepo.existsByTripIdAndUserIdAndRoleIn(
            tripId, userId, List.of(MemberRole.OWNER, MemberRole.EDITOR))) {
        throw GolaException.forbidden();
    }
    
    return trip;
}
```

### Pagination Pattern
```java
@GetMapping
public ResponseEntity<ApiResponse<PageResponse<TripResponse>>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search) {
    
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    return ResponseEntity.ok(ApiResponse.ok(
        tripService.list(SecurityUtils.getCurrentUserId(), search, pageable)));
}
```

### Soft Delete Pattern
```java
@Transactional
public void delete(UUID id, UUID userId) {
    var entity = repository.findActiveById(id)
        .orElseThrow(() -> GolaException.notFound("Resource"));
    
    // Authorization check
    if (!entity.getUserId().equals(userId)) {
        throw GolaException.forbidden();
    }
    
    // Soft delete
    entity.setDeletedAt(Instant.now());
    repository.save(entity);
    
    log.info("Resource {} soft deleted by user {}", id, userId);
}
```

## Testing Examples

### Service Unit Test
```java
@ExtendWith(MockitoExtension.class)
class TripServiceTest {
    @Mock
    private TripRepository tripRepo;
    
    @InjectMocks
    private TripService tripService;
    
    @Test
    void shouldCreateTrip_whenValidRequest() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateTripRequest req = CreateTripRequest.builder()
            .title("Summer Vacation")
            .build();
        
        // When
        when(tripRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        TripResponse response = tripService.createTrip(userId, req);
        
        // Then
        assertNotNull(response);
        assertEquals("Summer Vacation", response.getTitle());
        verify(tripRepo).save(any(Trip.class));
    }
}
```

### Controller Integration Test
```java
@SpringBootTest
@AutoConfigureMockMvc
class TripControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser
    void shouldCreateTrip_whenAuthenticated() throws Exception {
        String requestBody = """
            {
                "title": "Summer Vacation",
                "startDate": "2026-07-01T00:00:00Z",
                "endDate": "2026-07-15T00:00:00Z"
            }
            """;
        
        mockMvc.perform(post("/trips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("Summer Vacation"));
    }
}
```

## Troubleshooting Tips

### Common Errors and Solutions

**1. LazyInitializationException**
- **Cause**: Accessing lazy-loaded relationship outside transaction
- **Solution**: Use `@Transactional` or fetch eagerly with JOIN FETCH

**2. JWT Token Invalid**
- **Cause**: Token expired or wrong secret
- **Solution**: Check `gola.jwt.secret` and `gola.jwt.expiration` in config

**3. CORS Error**
- **Cause**: Frontend origin not allowed
- **Solution**: Add origin to `SecurityConfig.corsConfigurationSource()`

**4. N+1 Query Problem**
- **Cause**: Lazy loading in loop
- **Solution**: Use JOIN FETCH in query or DTO projection

**5. Transaction Not Rolling Back**
- **Cause**: Missing `@Transactional` or catching exceptions
- **Solution**: Add `@Transactional` and let exceptions propagate

## Best Practices Checklist

When implementing a new feature:

- [ ] DTOs created with validation annotations
- [ ] Entity has audit fields (createdAt, updatedAt, deletedAt)
- [ ] Repository filters out soft-deleted records
- [ ] Service methods use `@Transactional` for writes
- [ ] Authorization checks in place
- [ ] Current user extracted from SecurityContext
- [ ] Responses wrapped in `ApiResponse<T>`
- [ ] Pagination used for list endpoints
- [ ] OpenAPI annotations added
- [ ] Logging added for important events
- [ ] Error handling with GolaException
- [ ] Flyway migration created
- [ ] Tests written (unit + integration)
