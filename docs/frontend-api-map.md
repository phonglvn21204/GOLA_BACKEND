# GOLA Backend — Frontend API Map

**Base URL:** http://localhost:8081/api  
**Swagger:** http://localhost:8081/api/swagger-ui.html

## Response wrapper

All APIs return { success, message, data, error?, timestamp }.

**Auth:** Authorization: Bearer <accessToken>

## CORS (Vite :5173)

Allowed: http://localhost:5173, http://127.0.0.1:5173.  
withCredentials: true. Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS.

## Domain note

GOLA = travel/safety/social. **No course/lesson APIs.** Use /quests, /trips.

| FE area | GOLA endpoints |
|---------|----------------|
| auth | /auth/* |
| user | /me, /users/{id} |
| order | /me/orders, /me/orders/{id} |
| subscription | /me/subscriptions, /me/premium |
| payment | orders + /pricing/plans |
| notification | /notifications, /me/notifications/preferences |
| admin | /admin/* (ADMIN role) |

---

## Auth

| Endpoint | Method | Auth | Request | Response |
|----------|--------|------|---------|----------|
| /auth/register | POST | No | RegisterRequest | AuthResponse |
| /auth/login | POST | No | LoginRequest | AuthResponse |
| /auth/refresh | POST | No | {refreshToken} | AuthResponse |
| /auth/logout | POST | Yes | — | null |
| /auth/forgot-password | POST | No | {email} | null |
| /auth/reset-password | POST | No | token+password | null |

## User / profile

| Endpoint | Method | Auth | Request | Response |
|----------|--------|------|---------|----------|
| /me | GET | Yes | — | ProfileResponse |
| /me | PATCH | Yes | UpdateProfileRequest | ProfileResponse |
| /users/{id} | GET | Yes | — | ProfileResponse |
| /users/{id}/follow | POST/DELETE | Yes | — | null |

## Order & payment status

| Endpoint | Method | Auth | Response |
|----------|--------|------|----------|
| /me/orders | GET | Yes | OrderResponse[] |
| /me/orders/{id} | GET | Yes | OrderResponse |

## Subscription & premium

| Endpoint | Method | Auth | Response |
|----------|--------|------|----------|
| /me/premium | GET | Yes | PremiumStatusResponse |
| /me/subscriptions | GET | Yes | SubscriptionResponse[] |
| /pricing/plans | GET | No | PricingPlanResponse[] |

## Notification

| Endpoint | Method | Auth | Notes |
|----------|--------|------|-------|
| /notifications | GET | Yes | page, size |
| /notifications/unread-count | GET | Yes | |
| /notifications/mark-all-read | POST | Yes | |
| /me/notifications/preferences | GET | Yes | |
| /me/notifications/preferences/{type} | PUT | Yes | channel, isEnabled |

## Admin

| Endpoint | Method | Auth |
|----------|--------|------|
| /admin/metrics | GET | ADMIN |
| /admin/users | GET | ADMIN |
| /admin/users/{id}/role | PATCH | ADMIN |
| /admin/incidents | GET | ADMIN |
| /admin/posts | GET | ADMIN |
| /admin/posts/{id}/hide | PATCH | ADMIN |
| /admin/sos/active | GET | ADMIN |

## Trips (summary)

POST/GET/PATCH/DELETE /trips, stops, start/end, share, session, expenses, notes — all **Auth: Yes**.

## Safety, community, quests, live, AI

/sos/*, /incidents, /reports, /posts/*, /quests/*, /live/*, /ai/generate-trip — see Swagger.

---

Full tables + curl: docs/curl-examples.md  
Postman: docs/gola-api.postman_collection.json
