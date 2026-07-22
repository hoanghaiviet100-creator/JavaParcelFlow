# Parcel Flow — Logistics Order Management (MVP)

Spring Boot 3.3 / Java 17 backend. Layered architecture, JWT auth, Redis lockout/session,
Kafka email events, Flyway-managed MySQL schema.

## Milestone status
- [x] **M1 — Skeleton**: pom, application.yml, docker-compose, Flyway (V1+V2),
      21 JPA entities, 21 repositories, Swagger.
- [x] **M2 — Auth**: admin-only account creation, temp password via Kafka email event,
      forced password change, JWT (access + refresh), Redis lock escalation,
      single-session / IP policy, global exception handling, ApiResponse envelope.
- [x] **M3 — Core**: order creation (sender/receiver snapshot + parcels, transactional),
      order CRUD, parcel status update (custody log + current state + tracking event),
      tracking feed.
- [x] **M4 — Tests**: unit tests (lock escalation, order creation) + one integration test
      (login + lock) on Testcontainers.

Not implemented (Phase 2, intentionally deferred): routing, delivery assignment, hub scans.

## Prerequisites
JDK 17, Maven 3.9+, Docker + Docker Compose.

## Run
```bash
docker compose up -d        # MySQL + Redis + Kafka
mvn spring-boot:run         # Flyway applies V1 + V2; a dev ADMIN is bootstrapped
```
- Swagger UI: http://localhost:8080/swagger-ui.html
- Dev admin (from application.yml, disable in prod): `admin@parcelflow.local` / `Admin@12345`

## Auth flow (quick start)
1. `POST /api/v1/auth/login` as the dev admin -> access + refresh token.
2. `POST /api/v1/users` (ADMIN, Bearer token) with `{fullName,email,roleCode,hubId}` ->
   a temporary password is generated and logged by the Kafka email consumer
   (check the app console: "=== EMAIL (mock send) ===").
3. `POST /api/v1/auth/change-password` with `{email,currentPassword,newPassword}` ->
   clears the must-change flag.
4. `POST /api/v1/auth/login` with the new password -> tokens.

## Key endpoints
| Method | Path | Notes |
|--------|------|-------|
| POST | /api/v1/auth/login | public |
| POST | /api/v1/auth/refresh | public (rotates session) |
| POST | /api/v1/auth/change-password | public (pre-login) |
| POST | /api/v1/users | ADMIN — create account |
| POST | /api/v1/users/{id}/resend-temp-password | ADMIN |
| POST | /api/v1/users/{id}/unlock | ADMIN — clear permanent lock |
| POST | /api/v1/orders | authenticated |
| GET | /api/v1/orders/{id} | authenticated |
| GET | /api/v1/orders | authenticated (paged) |
| PUT | /api/v1/orders/{id} | authenticated |
| DELETE | /api/v1/orders/{id} | authenticated (logical cancel) |
| GET | /api/v1/orders/{id}/tracking-events | authenticated |
| GET | /api/v1/parcels/{id} | authenticated |
| PATCH | /api/v1/parcels/{id}/status | authenticated |

## Auth behaviour (as specified)
- Accounts are created only by ADMIN; role + hub are assigned at creation.
  A SHIPPER role auto-creates a `shipper_profiles` row.
- Temporary password expires (`password_expires_at`); the user must change it before first login.
- Lockout: 3 failed attempts -> 15-min Redis lock + 24h escalation flag.
  Failing 3 more times while escalated -> permanent lock (`is_active=false`); ADMIN unlocks.
- Single session per user in Redis. Login from a different IP while a session is active -> 403.
  Login again from the same IP -> old session invalidated, new tokens issued.

## Schema
- `V1__init_schema.sql` — the original schema, verbatim, minus `CREATE DATABASE`/`USE`.
- `V2__add_user_password_columns.sql` — the only change: `must_change_password`,
  `password_expires_at` on `users`. `orders.status` keeps its original 11 values
  (no `PARTIALLY_DELIVERED`).

## Tests
- `mvn test` runs the unit tests only (Surefire excludes `*IT`), so no Docker is needed.
- `AuthFlowIT` (login + lock) uses Testcontainers (MySQL + Redis) and requires Docker.
  Run it via the Failsafe plugin (`mvn verify` after adding the plugin) or by renaming it
  to `*Test`. It forces `ddl-auto=none` so Flyway alone owns the schema.

## Known caveat: MySQL ENUM + Hibernate `validate`
`ddl-auto` is `validate` per spec. MySQL `ENUM` columns are reported as `CHAR` over JDBC
while `@Enumerated(STRING)` maps to `VARCHAR`, which can raise a schema-validation error on
startup depending on the Hibernate/connector version. If that happens, set
`spring.jpa.hibernate.ddl-auto: none` — Flyway already guarantees the schema.

## Design notes
- Foreign keys are modelled as `Long` id-reference fields, not `@ManyToOne` associations,
  to keep the data layer lean for the MVP.
- `DELETE /orders/{id}` is a logical cancel (status -> CANCELLED) because a hard delete
  would violate parcel/tracking foreign keys.
- The Kafka email consumer logs the message instead of sending real email; swap in a
  provider later.
