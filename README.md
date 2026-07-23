# Parcel Flow

A parcel logistics system: order intake at a hub, routing between hubs, custody
tracking as the parcel changes hands, last-mile assignment to a shipper, and a
public tracking page for the customer.

- **Backend** — Spring Boot 3.3 / Java 17, MySQL + Flyway, Redis, Kafka
- **Frontend** — Next.js 16 (App Router), Redux Toolkit, TanStack Query, SCSS modules
- **Auth** — stateless JWT, one active session per user, temporary-password gate,
  brute-force lockout with escalation to a permanent lock

---

## Running it

Everything comes up with one command. Docker Desktop must be running.

```bash
docker compose up -d --build
```

First run takes a few minutes (Maven and npm both build from source). When it
settles:

| Service | URL | Notes |
|---|---|---|
| Frontend | http://localhost:3000 | |
| Backend API | http://localhost:8080 | |
| Swagger UI | http://localhost:8080/swagger-ui.html | |
| Health | http://localhost:8080/actuator/health | should report `UP` |
| Mailhog | http://localhost:8025 | catches outgoing mail, incl. temp passwords |
| MySQL | localhost:3307 | `root` / `admin123`, database `parcel_flow` |

Check that the stack is healthy:

```bash
docker compose ps
```

Stop it, keeping the database:

```bash
docker compose down
```

Stop it and wipe the database (next start re-runs all migrations and reseeds):

```bash
docker compose down -v
```

---

## Demo accounts

Seeded by migration `V4`, except the admin, which `AdminBootstrap` creates on
first startup from `app.bootstrap-admin.*`.

| Email | Password | Role | Hub |
|---|---|---|---|
| admin@parcelflow.local | `Admin@12345` | ADMIN | — |
| manager.hcm@parcelflow.local | `Manager@12345` | HUB_MANAGER | HCMC Main Hub |
| staff.hcm@parcelflow.local | `Staff@12345` | HUB_STAFF | HCMC Main Hub |
| dispatcher@parcelflow.local | `Dispatch@12345` | DISPATCHER | HCMC Main Hub |
| shipper1@parcelflow.local | `Shipper@12345` | SHIPPER | District 7 Branch |
| shipper2@parcelflow.local | `Shipper@12345` | SHIPPER | Thu Duc Branch |

These are development credentials committed on purpose so the project runs out
of the box. Override every one of them via environment variables before putting
this anywhere real — see the `environment:` block in `docker-compose.yml`.

---

## A five-minute demo path

1. **Log in** at http://localhost:3000/login as `staff.hcm@parcelflow.local`.
2. **Create an order** — pick a creating hub, fill sender and receiver, add one
   or more parcels. The API returns an order code like `OD20260722AW7FT5`.
3. **Move the parcel** through its lifecycle:
   `RECEIVED_AT_ORIGIN_HUB → WAITING_FOR_ROUTE → IN_TRANSIT → ARRIVED_AT_HUB →
   OUT_FOR_DELIVERY → DELIVERED`. Each change writes a custody log entry, updates
   the parcel's current state, appends to the customer timeline, and rolls the
   order status up.
4. **Track it publicly**, with no login, at
   `http://localhost:3000/tracking/result?code=<ORDER_CODE>`. Names and addresses
   stay hidden. Add `&phone=<receiver phone>` and they appear — with the phone
   number itself masked to its last three digits. This is what stops someone
   harvesting personal data by guessing order codes.
5. **Create a user** as the admin and watch the temporary password arrive in
   Mailhog at http://localhost:8025. That mail travels Kafka → consumer → SMTP.
   The new account cannot log in until it changes the password.
6. **Trip the lockout**: three wrong passwords returns `423 Locked`. After the
   temporary lock lapses, three more failures lock the account permanently and
   write the reason to `users.lock_reason`. Open the account from `/users`, and
   clear it with **Unlock account** on its detail page.

---

## Tests

Both layers are tested, and both run in CI (`.github/workflows/ci.yml`) on every
push and pull request.

### Backend — unit + integration

```bash
docker compose up -d mysql redis kafka mailhog
mvn -f parcel-flow-BE/parcel-flow-BE/pom.xml verify
```

`verify` runs unit tests under Surefire, then the integration tests under
Failsafe. The ITs start their own throwaway MySQL and Redis via Testcontainers
(Docker must be available) and cover the login/lockout flows, the order →
parcel → custody → tracking workflow, the validation and error-mapping rules,
the role-authorisation matrix, PII gating on public tracking, and the rate
limiter.

No local Maven? Run it in a container:

```bash
docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -v "$PWD/parcel-flow-BE/parcel-flow-BE:/app" -w /app -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9-eclipse-temurin-17 mvn -B verify
```

### Frontend — end-to-end (Playwright)

Drives a real browser against the running stack, so it catches broken routes,
guards and flows that unit tests miss. Bring the stack up first:

```bash
docker compose up -d --build
cd parcel-flow-fe/parcel-flow-fe
npm ci
npx playwright install chromium
npm run test:e2e
```

Specs live in `parcel-flow-fe/parcel-flow-fe/e2e/`: login and logout, the
role-guard redirect, creating an order through the form and landing on its
detail page, public tracking PII gating, and the admin account screens.

---

## Database

The backend owns its schema through Flyway and migrates automatically at
startup. Migrations live in
`parcel-flow-BE/parcel-flow-BE/src/main/resources/db/migration/`:

| Migration | What it does |
|---|---|
| `V1__init_schema.sql` | 21 tables: users, geography, hubs, orders, parcels, routing, custody, last mile, tracking |
| `V2__add_user_password_columns.sql` | temporary-password columns |
| `V3__add_user_lock_metadata.sql` | permanent-lock reason and timestamp |
| `V4__seed_reference_and_demo_data.sql` | provinces/districts/wards, parcel categories, six hubs, demo staff, delivery zones |

`V4` matters more than it looks: without it a fresh database has no geography
and no hubs, so no order can be created — every creating-hub and district id
would point at nothing. There is no API that can seed this data (the hub
endpoints are read-only), so it belongs in a migration.

### `database/parcel_flow_mysql.sql`

A standalone, single-file version of the same database for running MySQL on its
own — for coursework submission, for reading the design in one place, or for a
server without the application. It goes beyond the Flyway migrations while
keeping every table and column name identical, so no JPA entity changes:

- utf8mb4 throughout, for Vietnamese names and addresses
- 49 `CHECK` constraints carrying validation that otherwise lives only in Java
- composite foreign keys that make a ward/district/province mismatch unstorable
- 11 triggers keeping derived data correct (`orders.total_weight`,
  `parcel_current_state`, `parcel_categories.requires_special_handling`)
- 13 views for operations and reporting
- 8 stored procedures and functions for the scan, handover and tracking flows
- reference data plus a worked demo order

```bash
mysql -u root -p < database/parcel_flow_mysql.sql
```

**Do not run this against the database the application uses.** Flyway expects to
own that schema and refuses to start against a populated database it has no
history for. Use one or the other, not both.

---

## Layout

```
docker-compose.yml              the whole stack
README.md
database/
  parcel_flow_mysql.sql         standalone MySQL schema (see above)
parcel-flow-BE/parcel-flow-BE/  Spring Boot backend
  src/main/java/com/parcelflow/
    auth/        login, JWT, lockout, temporary passwords
    logistics/   orders, parcels, hubs, routing, assignments, tracking
    domain/      JPA entities
    repository/  Spring Data repositories
    security/    filter chain, JWT filter, entry points
    messaging/   Kafka producer/consumer for email
    common/      API envelope, error handling, enums
  src/main/resources/db/migration/   Flyway migrations
  src/test/                          unit tests + Testcontainers ITs
parcel-flow-fe/parcel-flow-fe/  Next.js frontend
  src/app/        App Router pages, grouped (public) and (dashboard)
  src/features/   feature slices with their API clients
  src/shared/     layouts, shared components
```

Both application folders are nested one level deeper than their parent
(`parcel-flow-BE/parcel-flow-BE/`) because they were unzipped that way; the
compose build contexts point at the inner folder.

---

## Known limitations

Worth stating plainly rather than discovering during a demo.

- **No fee calculation.** `orders.total_fee` is stored but always written as
  zero; there is no pricing engine.
- **Route plans and delivery assignments are read-only.** Both list pages render
  live API data, but nothing *creates* a route plan or assigns a parcel to a
  shipper over HTTP — that has to be done in SQL for now. The standalone schema
  file includes `sp_assign_shipper` for exactly this. Once an assignment exists,
  the shipper's own screens drive it to completion normally.
- **Addresses are entered as raw ids.** The create-order form asks for
  "Sender District ID" and "Sender Province ID" as numbers, because the backend
  exposes no province/district/ward endpoint for the frontend to build pickers
  from. With the seeded data: province 1 = HCMC (districts 1–4), 2 = Ha Noi
  (5–6), 3 = Da Nang (7).
- **Parcel status transitions are not validated by the application.** Any status
  is accepted, so a delivered parcel can be moved back to created. The
  standalone schema file encodes the legal state machine; the Java layer does
  not yet enforce it.
- **One active session per user.** Logging in from a second IP is rejected while
  a session is live, and refreshing rotates the access token, which invalidates
  the previous one. This is deliberate, but it surprises people testing with two
  browsers at once.
