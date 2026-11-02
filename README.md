# Managable — Multi-Tenant Projects & Tasks Platform

A full-stack Spring Boot + React workspace built around the parts that usually get hand-waved: **refresh-token theft detection that can tell an attacker from a second browser tab**, **multi-tenant project isolation (`404` across tenants, `403` within a project by role)**, optimistic locking that rejects stale writes instead of silently losing them, a database schema owned by SQL migrations (`V1`–`V12`) that every test run re-applies against real PostgreSQL, and structured logs where a security event is a queryable object rather than a sentence.

**Java 25 · Spring Boot 4.0.6 · Spring Security 7 · PostgreSQL 18 · Flyway · JPA · Testcontainers · React 18 · Vite · Nginx · Docker**
— 84 integration tests, all green.

---

## Four things worth two minutes

**1. A stolen refresh token and a second browser tab look identical. This tells them apart.**
Every refresh rotates. So a token presented twice is either a replay or two tabs racing — and revoking the wrong one logs a real user out of every device. One `UPDATE` decides the winner ([`RefreshTokenRepository.markRotated`](src/main/java/com/example/demo/auth/repository/RefreshTokenRepository.java)):

```sql
UPDATE refresh_tokens SET status = 'ROTATED', rotated_at = :now
 WHERE token_hash = :hash AND status = 'ACTIVE'
   AND expires_at > :now AND absolute_expires_at > :now
```

Under `READ COMMITTED` the losing transaction blocks on the row lock, re-evaluates the `WHERE` against the committed new row, and gets `0` back. A `rotated_at` timestamp then separates the two causes: rotated 8 ms ago is a sibling tab (reject, revoke nothing); rotated three days ago is theft (revoke the whole token family). Every branch returns an **identical 401** — telling a caller "that token was already used" confirms to an attacker that they hold a real credential.

**2. A security event that survives the request that detected it.**
Reuse detection ends by throwing, which rolls the transaction back — so a same-transaction revoke would be undone and the attacker would keep a working session. `revokeFamily` runs `REQUIRES_NEW`, on the **repository** interface rather than the service, because `@Transactional` lives on a proxy outside the bean and a `this.method()` call silently skips it. [`SelfInvocationTransactionDemoTest`](src/test/java/com/example/demo/auth/SelfInvocationTransactionDemoTest.java) pins that with two byte-identical methods leaving `0` rows and `1`.

**3. Tests run against real PostgreSQL, built by the real migration chain.**
Testcontainers starts an empty Postgres 18; Flyway applies `V1`–`V12` exactly as it does in production, and `ddl-auto=validate` then checks every entity against the result. A column an entity expects and a migration forgets fails the build, not a deployment. No embedded stand-in reproduces `uuidv7()`, ICU collations, `JSONB` activity payloads, or `UPDATE ... WHERE version = ?` faithfully enough to be worth it.

**4. Someone else's project or task is a `404`, not a `403`.**
Cross-tenant access checks run in [`ProjectSecurity`](src/main/java/com/example/demo/project/ProjectSecurity.java) and [`TaskSecurity`](src/main/java/com/example/demo/tasks/TaskSecurity.java) before the controller executes: if the caller is not a member of the project, the security bean throws `ResourceNotFoundException` (`404 Not Found`), never `403 Forbidden`, preventing UUID enumeration across tenants. Once membership is established, role-based rules (`MANAGER` vs `MEMBER`) return `403 Forbidden` for in-project privilege boundaries.

Every log line carries a `requestId` from an `X-Request-Id`-aware filter and a `userId` from the MDC, and under `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` a token-theft event emits as:

```json
{"message":"reuse detected","log":{"level":"WARN"},"userId":"01a05c34-…",
 "requestId":"d359b55f-…","familyId":"019ca945-…","rotatedAgoSeconds":31}
```

---

## Run it

```bash
docker compose up --build
```

Compose starts three containers:
1. **`postgres` (`:5433 -> :5432`)** — PostgreSQL 18 with a TCP `pg_isready` healthcheck and persistent `pgdata` volume.
2. **`app` (`:8080`)** — Spring Boot API (defaults to `SPRING_PROFILES_ACTIVE=dev` in Compose so Flyway applies `V1`–`V12` plus the demo seed [`R__seed.sql`](src/main/resources/db/dev-migration/R__seed.sql)).
3. **`frontend` (`:5173 -> :80`)** — React SPA built with Node 22 and served by Nginx 1.27, which reverse-proxies `/api/` and `/auth/` to `http://app:8080` so the browser stays on one origin and sends the `SameSite=Strict` refresh cookie.

| Service | URL | Notes |
|---|---|---|
| Web UI (Managable) | <http://localhost:5173> | Demo logins: `alice@demo.local` / `bob@demo.local` (password: `password123`) |
| REST API | <http://localhost:8080/api/projects> | Bearer JWT required |
| Swagger UI | <http://localhost:8080/docs> | Interactive OpenAPI explorer |
| Health | <http://localhost:8080/actuator/health> | Public health probe |

<details>
<summary><b>Running locally without Docker Compose</b> (JDK 25, Node 22+, PostgreSQL 18)</summary>

```bash
createdb task_api
cp example.env secrets.env      # fill in url/username/password and app.jwt.secret
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

In a second terminal, start the Vite dev server (which proxies `/api` and `/auth` to `:8080`):

```bash
cd frontend
npm install
npm run dev                     # http://localhost:5173
```

**Do not run the migration files by hand.** Flyway owns the schema and records what it applied in `flyway_schema_history`.

</details>

```bash
./mvnw test        # 81 tests; needs only a Docker daemon, no local database or credentials
```

---

## API

### Auth

| Method | Path | Presents | Success | Errors |
|---|---|---|---|---|
| `POST` | `/auth/otp/send` | `email` | 200 — generates & logs 6-digit OTP (`5m` TTL) | 400 · 409 duplicate email |
| `POST` | `/auth/otp/verify` | `email`, `otp` (6 digits) | 200 — marks OTP verified | 400 invalid/expired OTP |
| `POST` | `/auth/signup` | `username`, `email`, `password`, `otp?` | 200, empty body | 400 · 409 duplicate email/username |
| `POST` | `/auth/login` | `email`, `password` | 200 + `Set-Cookie` | 400 · 401 |
| `POST` | `/auth/refresh` | `refreshToken` cookie | 200 + `Set-Cookie` | 400 no cookie · 401 |
| `POST` | `/auth/logout` | `refreshToken` cookie | 204 + clears cookie | — (idempotent) |
| `POST` | `/auth/logout-all` | Bearer token | 204 + clears cookie | 401 |
| `GET` | `/auth/me` | Bearer token | 200 (`id`, `username`, `email`, `role`) | 401 |

Both `login` and `refresh` return the short-lived access token in the JSON body and the refresh token in an `HttpOnly` cookie scoped to `Path=/auth/refresh`.

### Projects

All project routes require authentication. Non-members receive `404` on every verb; project `MEMBER`s receive `403` on manager-only mutations (`PUT`, `DELETE`, and member management).

| Method | Path | Role | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/projects` | Member | 200 — projects caller belongs to, with caller's `role` and `memberCount` | 401 |
| `POST` | `/api/projects` | Authenticated | 201 + `Location` (creator becomes `MANAGER`) | 400 |
| `GET` | `/api/projects/{id}` | Member | 200 | 404 |
| `PUT` | `/api/projects/{id}` | Manager | 200 — checks `version` and `dueDate >= startDate` | 400 · 403 · 404 · 409 |
| `DELETE` | `/api/projects/{id}?version=n` | Manager | 204 | 403 · 404 · 409 |
| `GET` | `/api/projects/{id}/members` | Member | 200 — list of project members and roles | 404 |
| `POST` | `/api/projects/{id}/members` | Manager | 200 — add member by `email` and `role` | 400 · 403 · 404 |
| `PATCH` | `/api/projects/{id}/members/{memberId}` | Manager | 200 — change member role | 400 · 403 · 404 · 409 sole manager |
| `DELETE` | `/api/projects/{id}/members/{memberId}` | Manager | 204 — remove member | 403 · 404 · 409 sole manager |
| `GET` | `/api/projects/{id}/activity` | Member | 200 — newest 50 audit entries (`project_activity_logs`) | 404 |

### Tasks

Every task belongs to a project (`projectId`) and has an optional `assigneeId` (who must be a member of that project). In a solo project (1 member), tasks auto-assign to the creator.

| Method | Path | Role | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/tasks?title=&done=` | Member | 200 — tasks across all projects the caller belongs to | 400 |
| `GET` | `/api/tasks/{id}` | Member | 200 | 404 |
| `POST` | `/api/tasks` | Member | 201 + `Location` | 400 · 404 |
| `PUT` | `/api/tasks/{id}` | Manager or Assignee | 200 — full update with optimistic locking | 400 · 403 · 404 · 409 |
| `PATCH` | `/api/tasks/{id}/done` | Manager or Assignee | 200 — toggles `done` with `version` check | 400 · 403 · 404 · 409 |
| `DELETE` | `/api/tasks/{id}?version=n` | Manager | 204 | 400 · 403 · 404 · 409 |

### 5-Second Deletion Grace Period (UI)

Deleting a task from the Inspector or deleting a project from the Members sheet does not fire `DELETE` immediately:
1. The item is optimistically removed from the workspace and a **5-second countdown toast** (`5s` $\rightarrow$ `1s`) appears with an **Undo** button and progress bar.
2. Clicking **Undo** cancels the timer and restores the task (or the project and all its tasks) without touching the server.
3. Once the 5-second grace window expires, the `DELETE` request is sent with the item's `version`; if the server rejects it (e.g., `409 Conflict`), the UI rolls back the deletion and surfaces the conflict.

---

## Design notes

- **The schema is the source of truth (`V1`–`V12`).** `ddl-auto=validate` ensures Hibernate never creates or alters tables; it verifies entities against the Flyway schema at startup.
  - `V1`–`V5`: `tasks`, `users` (UUIDv7 PK, ICU case-insensitive email), `refresh_tokens`, ownership backfill, and UUIDv7 task PKs.
  - `V6`–`V12`: `projects`, `projects_users` join table with per-project roles (`MANAGER`, `MEMBER`), task-to-project foreign keys, `username` on `users`, and `project_activity_logs` (`JSONB` change payloads).
- **Sole-manager invariant.** A project must always have at least one `MANAGER`. Demoting or removing the last manager throws `ProjectConflictException` (`409 Conflict`).
- **Filter-chain `401` and `403` errors are RFC 7807 `application/problem+json`.** [`SecurityConfig`](src/main/java/com/example/demo/auth/config/SecurityConfig.java) delegates `AuthenticationEntryPoint` and `AccessDeniedHandler` to `HandlerExceptionResolver` so security errors share the exact same RFC 7807 envelope as controller exceptions.
- **Dev vs. Prod logging hygiene.** SQL parameter bind logging (`org.hibernate.orm.jdbc.bind=TRACE`) and `DEBUG` logging live exclusively in [`application-dev.properties`](src/main/resources/application-dev.properties) so sensitive parameters (such as token or password hashes) are never logged in production.

---

## Testing

| Suite | Tests | Scope |
|---|---|---|
| [`TaskApiTest`](src/test/java/com/example/demo/tasks/TaskApiTest.java) | 31 | HTTP contract via MockMvc — statuses, `problem+json`, optimistic locking, and multi-tenant/RBAC boundaries |
| [`TaskRepositoryTest`](src/test/java/com/example/demo/tasks/repository/TaskRepositoryTest.java) | 16 | Repository queries and constraints through real committed transactions |
| [`AuthApiTest`](src/test/java/com/example/demo/auth/AuthApiTest.java) | 18 | OTP send/verify, signup, login, `/auth/me`, `/auth/logout`, `/auth/logout-all`, duplicate email `409` |
| [`RefreshApiTest`](src/test/java/com/example/demo/auth/RefreshApiTest.java) | 10 | Token rotation, two-tab grace window, theft detection, sliding vs. absolute expiry |
| [`ProjectApiTest`](src/test/java/com/example/demo/project/ProjectApiTest.java) | 7 | Project lifecycle, date validation, `404` non-member isolation, `403` member RBAC, `409` version & sole-manager checks, activity logs |
| [`SelfInvocationTransactionDemoTest`](src/test/java/com/example/demo/auth/SelfInvocationTransactionDemoTest.java) | 1 | Proves `@Transactional(propagation = REQUIRES_NEW)` is bypassed on self-invocation |
| [`Demo1ApplicationTests`](src/test/java/com/example/demo/Demo1ApplicationTests.java) | 1 | Context startup + Hibernate schema validation against all 12 Flyway migrations |

---

## Future enhancements

- **Password complexity policy:** Signup currently enforces `@NotBlank`; adding length/entropy rules would harden registration.
- **Distributed lock on token sweep:** `SweepingService` is idempotent, but adding ShedLock or `pg_try_advisory_lock` avoids redundant sweeps across multiple replicas.
- **Pagination:** List endpoints currently return full member-scoped lists; cursor or `Page` envelopes can be added as workspaces grow.
