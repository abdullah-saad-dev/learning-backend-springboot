# Tasks API

A Spring Boot REST API built around the parts that usually get hand-waved: **refresh-token theft
detection that can tell an attacker from a second browser tab**, optimistic locking that rejects
stale writes instead of silently losing them, and a database schema owned by SQL migrations that
every test run re-applies against real PostgreSQL.

**Java 25 · Spring Boot 4.0.6 · Spring Security 7.0.5 · PostgreSQL 18 · Flyway · JPA · Testcontainers · Docker**
— 58 tests, all green.

---

## Three things worth two minutes

**1. A stolen refresh token and a second browser tab look identical. This tells them apart.**
Every refresh rotates. So a token presented twice is either a replay or two tabs racing — and
revoking the wrong one logs a real user out of every device. One `UPDATE` decides the winner
([`RefreshTokenRepository.markRotated`](src/main/java/com/example/demo/auth/repository/RefreshTokenRepository.java)):

```sql
UPDATE refresh_tokens SET status = 'ROTATED', rotated_at = :now
 WHERE token_hash = :hash AND status = 'ACTIVE'
   AND expires_at > :now AND absolute_expires_at > :now
```

Under `READ COMMITTED` the losing transaction blocks on the row lock, re-evaluates the `WHERE`
against the committed new row, and gets `0` back. A `rotated_at` timestamp then separates the two
causes: rotated 8 ms ago is a sibling tab (reject, revoke nothing); rotated three days ago is theft
(revoke the whole token family). Every branch returns an **identical 401** — telling a caller "that
token was already used" confirms to an attacker that they hold a real credential.

**2. A security event that survives the request that detected it.**
Reuse detection ends by throwing, which rolls the transaction back — so a same-transaction revoke
would be undone and the attacker would keep a working session. `revokeFamily` runs
`REQUIRES_NEW`, on the **repository** interface rather than the service, because `@Transactional`
lives on a proxy outside the bean and a `this.method()` call silently skips it.
[`SelfInvocationTransactionDemoTest`](src/test/java/com/example/demo/auth/SelfInvocationTransactionDemoTest.java)
pins that with two byte-identical methods leaving `0` rows and `1`.

**3. Tests run against real PostgreSQL, built by the real migration chain.**
Testcontainers starts an empty Postgres 18; Flyway applies `V1`–`V3` exactly as it does in
production, and `ddl-auto=validate` then checks every entity against the result. A column an entity
expects and a migration forgets fails the build, not a deployment. No embedded stand-in reproduces
`uuidv7()`, ICU collations, or `UPDATE ... WHERE version = ?` faithfully enough to be worth it.

---

## Run it

```bash
export APP_JWT_SECRET=$(openssl rand -base64 32)   # HS256 needs ≥ 256 bits
docker compose up --build
```

Compose starts PostgreSQL, waits for it to be genuinely ready, then starts the API on **8080**
(the database is published on **5433** so it cannot collide with a local one). No JDK required.

> **Known gap:** `docker-compose.yml` does not yet forward `APP_JWT_SECRET` to the `app` service.
> Add it under that service's `environment:` block, or startup fails on an unresolvable
> `${app.jwt.secret}`.

<details>
<summary><b>Running locally instead</b> (JDK 25, PostgreSQL 18, Docker for the tests)</summary>

```bash
createdb task_api
cp example.env secrets.env      # then fill in url/username/password
./mvnw spring-boot:run
```

`secrets.env` is gitignored and imported via
`spring.config.import=optional:file:./secrets.env[.properties]`; `optional:` is what lets CI run
with no credentials at all. **Add `app.jwt.secret` yourself** — it has no default and is missing
from `example.env`, so the app will not start without it.

**Do not run the migration files by hand.** Flyway owns the schema and records what it applied in
`flyway_schema_history`. A database with tables but no such table is one Flyway did not build, and
its next run fails on *"relation already exists"*.

| | | Token required |
|---|---|---|
| API | <http://localhost:8080/api/tasks> | yes |
| Auth | <http://localhost:8080/auth/login> | no |
| Swagger UI | <http://localhost:8080/docs> | no |
| Health | <http://localhost:8080/actuator/health> | no |
| Route index | <http://localhost:8080/> | yes |

</details>

```bash
./mvnw test        # 58 tests; needs only a Docker daemon, no database, no credentials
```

---

## API

### Auth

| Method | Path | Presents | Success | Errors |
|---|---|---|---|---|
| `POST` | `/auth/signup` | — | 200, empty body | 400 · 409 duplicate email |
| `POST` | `/auth/login` | email + password | 200 + `Set-Cookie` | 400 · 401 |
| `POST` | `/auth/refresh` | `refreshToken` cookie | 200 + `Set-Cookie` | 400 no cookie · 401 |

Both `login` and `refresh` return the access token in the body and the refresh token in a cookie:

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "issuedAt": "...", "expiresAt": "..." }
```
```http
Set-Cookie: refreshToken=…; Path=/auth/refresh; Max-Age=604800; Secure; HttpOnly; SameSite=Strict
```

The split is deliberate: the client must be able to *read* the access token to set an
`Authorization` header, while the refresh token must stay out of JavaScript. `Path=/auth/refresh`
keeps the long-lived credential off every other request.

```bash
curl http://localhost:8080/api/tasks -H "Authorization: Bearer $ACCESS_TOKEN"
```

The access token is HS256, lives 15 minutes, and carries `sub` (user UUID) and a bare `role` claim
— no PII, since claims are readable by anyone. `Secure` on the cookie means a browser will not
store it over plain `http://localhost`; correct for production, and worth knowing when manual
testing looks inexplicably broken.

### Tasks

Authenticated. Every mutating request carries the `version` the client last read.

| Method | Path | Success | Errors |
|---|---|---|---|
| `GET` | `/api/tasks?title=&done=` | 200 — newest first, both filters optional and composable | 400 |
| `GET` | `/api/tasks/{id}` | 200 | 404 |
| `POST` | `/api/tasks` | 201 + `Location` | 400 |
| `PUT` | `/api/tasks/{id}` | 200 — full replacement | 400 · 404 · 409 |
| `PATCH` | `/api/tasks/{id}/done` | 200 — sets `done` only | 400 · 404 · 409 |
| `DELETE` | `/api/tasks/{id}?version=n` | 204 | 400 · 404 · 409 |

```json
{ "id": 1, "title": "write the schema", "details": "DDL for the tasks table",
  "createdAt": "2026-07-31T12:36:49.962157Z", "done": true, "version": 1 }
```

`id`, `createdAt` and `version` are server-owned. `version` is published anyway, because a client
cannot edit safely without it. `done` is a boxed `Boolean` with `@NotNull` everywhere it appears —
a primitive would default an omitted field to `false` and quietly mark a task pending instead of
answering 400.

### Errors

Every error is `application/problem+json` (RFC 7807), framework-generated validation failures
included:

```json
{ "title": "Conflicting change", "status": 409, "instance": "/api/tasks/1",
  "detail": "Task with id 1 has moved on: expected version 0 but the stored version is 1" }
```

Controllers contain no `try`/`catch`. Services throw domain exceptions that know nothing about
HTTP; [`ApiExceptionHandler`](src/main/java/com/example/demo/ApiExceptionHandler.java) decides what
they mean over the wire, registered at **lowest precedence** so Spring's own advice keeps first
refusal on the errors it already renders — otherwise every malformed-JSON 400 becomes a 500.

---

## Design notes

**The schema is the source of truth.** `ddl-auto=validate`: Hibernate never creates or alters a
table, it compares entities to the schema at startup and refuses to boot on a mismatch. Flyway
applies `V1` tasks (integer identity PK, `version`), `V2` users (UUID v7 PK, ICU case-insensitive
`email`, `CHECK` on `role`), `V3` refresh tokens (unique `token_hash`, FK `ON DELETE CASCADE`).
The two are complementary: Flyway guarantees the schema was *built* the same way everywhere,
validation guarantees the *entities still match it*.

**Two tokens, two jobs.** The access token is stateless — verifying it is a signature check, no
database round trip — and nothing can revoke it, which is exactly why it lives 15 minutes. The
refresh token is 32 bytes of `SecureRandom`, *not* a JWT: a JWT cannot be revoked, and if the
server consults a table anyway then the signature buys nothing while inviting a later
"optimization" that drops the lookup and with it revocation.

**SHA-256 for the token hash, not bcrypt.** A login has two columns — an email to *find* the row,
a hash to *verify* it. A refresh token is one value doing both jobs, so the lookup must be an
equality match. bcrypt is salted, therefore non-deterministic, therefore turns every refresh into a
full table scan at bcrypt cost per row. Slow hashing compensates for low entropy; 256 bits of
`SecureRandom` has no deficit to compensate for.

**Two clocks, one of which slides.** `expires_at` gets seven fresh days on every rotation, so an
active user is never logged out. `absolute_expires_at` is **copied unchanged** parent to successor,
capping the family at 30 days from the original login. That copy is the load-bearing line —
recompute it and the session is immortal, which is what
`RefreshApiTest.theAbsoluteCapEventuallyEndsTheSession` exists to catch. The same cap derives the
cleanup predicate: past it, no descendant can still be live, so `SweepingService` deletes on
exactly `absolute_expires_at < :now`, nightly at 03:00. Derived from the design, not picked from a
hat.

**Optimistic locking, checked at two layers.** `TaskService` compares the client's `version`
against the stored one — catching two people editing from copies read in earlier requests — and
Hibernate emits `UPDATE ... WHERE id = ? AND version = ?`, catching a writer that commits *inside*
the current transaction's window. Neither covers the other. A `@Version` column alone is not
enough; publishing the version to clients is what closes the gap.

<details>
<summary><b>Optimistic locking versus compare-and-swap</b> — both branch on a row count, for different problems</summary>

| | `@Version` on `Task` | CAS on `refresh_tokens` |
|---|---|---|
| Question | has this row changed since I read it? | is this token still spendable — and if so, spend it |
| Problem | lost update: two clients edit stale copies | one-shot resource: spendable exactly once |
| Failure means | conflict; re-read and retry → `409` | not authorized; possibly theft → `401` |
| Client sees | its own version echoed back | nothing; all failures look alike by design |

`@Version` cannot do the refresh token's job: the security predicate (`status`, both expiries) is
not expressible in a version number, so it would still be checked in Java after a read —
reintroducing the read-then-act gap the CAS exists to close. It also surfaces as an exception at
flush time, which cannot distinguish a racing tab from an attacker. That distinction is the feature.

</details>

**Transactions belong to the service.** The unit of work is a service call, not a store call, so a
read and the write depending on it commit or roll back together. `open-in-view` is explicitly
disabled: left on, entities handed to the web layer stay managed and a stray setter flushes to the
database.

**One query instead of a finder per filter.** `TaskRepository.search` neutralises each predicate on
a null argument (`cast(:title as String) is null or ...`), so two optional filters mean one method
instead of four. **The casts are load-bearing** — PostgreSQL plans the whole statement before
evaluating any of it, so an untyped null resolves `upper()` to `upper(bytea)`, which does not
exist, and the query fails outright even on the branch a null `title` never reaches.

**Input types are separate from the entity.** A client-supplied `id` or `createdAt` structurally
cannot reach the store, because the field does not exist on the request type. `Task` deliberately
avoids Lombok's `@Data`, which would derive `equals`/`hashCode` from mutable fields — a task's hash
would change the moment Hibernate assigned its id, losing it from any `HashSet` holding it.

**Multi-stage Docker build.** The JDK stays in the build stage; only the jar and a JRE ship. Local
`docker images` reports 478 MB for `eclipse-temurin:25-jre` against 590 MB for `25-jdk` — roughly
110 MB of base image that never ships. Non-root user, cached dependency layer, container-aware heap.

---

## Testing

| Suite | Tests | Scope |
|---|---|---|
| `TaskApiTest` | 23 | HTTP contract via MockMvc — statuses, media types, the version handshake |
| `AuthApiTest` | 12 | A password becomes a token; that token opens a protected route |
| `TaskRepositoryTest` | 11 | The store, through real committed transactions |
| `RefreshApiTest` | 10 | Rotation, the two-tab race, reuse detection, both expiries, cookie attributes |
| `SelfInvocationTransactionDemoTest` | 1 | `REQUIRES_NEW` is skipped on a self-call |
| `Demo1ApplicationTests` | 1 | Context loads — where Hibernate validates every entity and Spring parses every `@Query` |

`RefreshApiTest` injects a movable `Clock`: the 30-second grace window and the 30-day cap are
unreachable against a clock that will not move, which is why time enters the service as an injected
`Clock` and reaches SQL as a bound `:now` rather than `now()`.

**Those tests were checked by mutation, not by their own green result.** Recomputing
`absoluteExpiresAt` instead of copying it fails two of them; swapping the grace-window branches
fails two others. An earlier version of `theSuccessorInheritsTheFamilyAndTheAbsoluteCapUnchanged`
*survived* the first mutation, because login and refresh happened at the same frozen instant and
the recomputed cap coincidentally equalled the copied one. The clock now advances an hour first.
A frozen clock can make two different behaviours look identical.

---

## Known gaps

Listed because they are known, scoped, and deliberate — not because they were missed.

- **No logging in the auth package.** Since all five refresh-failure branches return an identical
  401 *by design*, the log is the only channel that still knows the difference — so a family
  revocation currently leaves no trace anywhere. This is the largest gap.
- **`docker-compose.yml` does not forward `APP_JWT_SECRET`**, and `example.env` omits
  `app.jwt.secret`, so both documented setup paths need one manual line on a clean machine.
- **Authorization is authentication-only.** `tasks` has no `owner_id`; every authenticated user
  sees every task. Closing it needs expand/migrate/contract plus `WHERE owner_id = ?` — the
  principal's `getName()` already returns the user UUID.
- **No logout.** `revokeFamily` is exactly the operation needed and already exists; nothing calls
  it outside reuse detection.
- **Bearer-token 401s are not `problem+json`.** `AuthenticationEntryPoint` writes the response
  before `DispatcherServlet` exists, so `@ExceptionHandler` never sees it. The fix is
  `exceptionHandling(...)` on the filter chain.
- **`R__seed.sql` sits in `db/migration/`**, so the dev seed runs in every environment. It belongs
  behind a dev-only `spring.flyway.locations`.
- **Lifetimes are constants, not configuration** — 15 min / 7 days / 30 days / 30 s live in
  `JwtService`, `RefreshTokenService` and `AuthController`. `app.jwt.accesstoken-validity-minutes`
  is declared in `application.properties` and **read by nothing**.
- **Password policy is `@NotBlank` only.** A one-character password is accepted at signup.
- **The nightly sweep discards its row count** and runs on every replica. Idempotent, so safe, but
  duplicated; ShedLock or `pg_try_advisory_lock` is the production answer.
- **No CI pipeline, no pagination.** `./mvnw test` needs only a Docker daemon, so a workflow is
  cheap; list endpoints return every match, which a `Page` envelope would fix.
