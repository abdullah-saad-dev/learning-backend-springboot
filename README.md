# Tasks API

A small REST API for a to-do list, built to practise two things properly: **HTTP semantics** and **correctness under concurrent access**.

Storage is an in-memory map by design — there's no database, and that's deliberate. The interesting part of this project isn't where the data lives; it's the decisions documented below, most of which changed at least once as their consequences became clear.

**Stack:** Java 25 · Spring Boot 4.0.6 · Maven · springdoc-openapi · JUnit 5

---

## Running it

```bash
./mvnw spring-boot:run
```

| | |
|---|---|
| API | <http://localhost:8080/api/tasks> |
| Interactive docs (Swagger UI) | <http://localhost:8080/docs> |
| OpenAPI spec | <http://localhost:8080/v3/api-docs> |
| Health | <http://localhost:8080/health> |

```bash
./mvnw test
```

---

## Endpoints

**`TaskController`** — `/api/tasks`

| Method | Path | Success | Errors |
|---|---|---|---|
| `GET` | `/api/tasks` | 200 — all tasks | — |
| `GET` | `/api/tasks/done` | 200 — completed only (may be `[]`) | — |
| `GET` | `/api/tasks/pending` | 200 — not-yet-done only (may be `[]`) | — |
| `GET` | `/api/tasks/{title}` | 200 — one task | 404 |
| `POST` | `/api/tasks` | 201 + `Location` | 400 blank title · 409 duplicate |
| `PUT` | `/api/tasks/{title}` | 200 — full replacement | 400 · 404 · 409 rename collision |
| `PATCH` | `/api/tasks/{title}` | 200 — toggles `done` only | 400 · 404 |
| `DELETE` | `/api/tasks/{title}` | 204 | 404 |

**`MetaController`** — root

| Method | Path | Returns |
|---|---|---|
| `GET` | `/` | API name, version, and a live list of every route |
| `GET` | `/health` | `{"status":"ok"}` |

### Data model

```json
{
  "id": "4cfed96b-ccd1-4f6a-8b92-f0290939ab44",
  "title": "buy milk",
  "details": "2%",
  "createdAt": "2026-07-16T12:50:38.839660600Z",
  "done": false
}
```

`id` and `createdAt` are **server-owned** — a client can never set or change them. `PUT` preserves both across a rename.

---

## Design decisions

### Titles are unique, enforced with 409

Originally duplicate titles were allowed, with `GET`/`DELETE` acting on every match. That turned out to be untenable, and the reason is worth stating precisely: **`/api/tasks/{title}` stops being a resource identifier and becomes a search query.** The symptoms all followed from that one flaw — `GET` returned an array from a single-resource URL, `DELETE` became a bulk delete, and `PUT`/`PATCH` had no way to pick a target, so duplicate-titled tasks were *permanently uneditable*. `POST` also handed back the same `Location` header for two different tasks, which makes the header meaningless.

Forbidding duplicates fixed every one of those at once and deleted an entire exception class that existed only to paper over the ambiguity.

Uniqueness is **case-insensitive**, matching lookups — otherwise you could create two tasks that no URL can tell apart. It's enforced on **both** `POST` and `PUT`, since a rename is equally capable of creating a duplicate.

### PUT and PATCH mean different things

`PUT` is a full replacement: fields you omit are reset to their defaults. `PATCH` applies a partial change and leaves the rest alone. Consequently `PUT {"title":"buy milk"}` on a completed task *will* mark it pending again — correct full-replacement semantics, and precisely why flipping state belongs in `PATCH`.

`PATCH` is deliberately scoped to `done` only. Generalising "null means leave unchanged" to `details` would break it: `details` can legitimately *be* null, so "absent" and "clear this field" collapse into the same value. Solving that properly needs JSON Merge Patch or `Optional` wrappers — machinery this API doesn't need.

### Input DTOs are separate from the domain record

`TaskRequest` and `TaskPatch` are distinct types from `Task`. Not because a client could exploit it today — `create()` mints its own UUID regardless — but because the separation is what keeps that true as the code grows. A client-supplied `id` structurally cannot reach the store: the field doesn't exist on the input type. The moment `Task` grows an `ownerId`, binding the entity directly would make it client-writable by default, and nothing would fail to warn you. That's the mass-assignment class of bug.

`TaskPatch.done` is a boxed `Boolean` specifically so `@NotNull` is enforceable — a primitive would silently default an omitted field to `false`, and `PATCH {}` would mark the task pending instead of returning 400.

### Concurrency: a thread-safe map is not a thread-safe service

`TaskService` is a Spring singleton, so one instance is shared by every request thread. It stores tasks in a `ConcurrentHashMap`, but **that alone is not enough**, and this was the sharpest lesson in the project.

`ConcurrentHashMap` makes *single-key* operations atomic. It cannot protect an invariant that spans entries — and "no two tasks share a title" is a fact about the whole map. So every mutator does a check-then-act (`exists(title)` → `put(...)`) with a gap that another thread can act inside. Three real bugs lived there: lost updates, resurrection of a deleted task by a racing update, and duplicate titles slipping past the uniqueness check.

All four mutators are therefore `synchronized`. Reads stay lock-free and weakly consistent, which is fine for `GET`.

`synchronized` rather than `ReentrantLock` on purpose: these operations need plain mutual exclusion and nothing a lock adds (`tryLock`, timeouts, conditions, fairness). A monitor also can't be leaked — it releases on scope exit, including when `single()` throws mid-region, whereas a missing `finally { unlock(); }` would wedge every future write after the first 404. The virtual-thread pinning objection to `synchronized` was resolved by JEP 491 in JDK 24; this runs on Java 25.

### Errors are RFC 7807

Every error — including framework-generated validation failures — is `application/problem+json`, via `@RestControllerAdvice` returning `ProblemDetail`:

```json
{
  "detail": "Task with title buy milk already exists",
  "instance": "/api/tasks",
  "status": 409,
  "title": "Duplicate task title"
}
```

Controllers contain no `try`/`catch`. The service throws domain exceptions that know nothing about HTTP; the web layer decides what they mean over HTTP.

### `GET /` describes itself

The route list isn't hand-maintained. It's read from Spring's `RequestMappingHandlerMapping` — the same table the dispatcher routes on — filtered to this application's own controllers, so springdoc's and Boot's internal endpoints don't leak in. A hardcoded list drifts the moment someone adds a mapping; this one can't, because it isn't a description of the routes, it *is* the routes.

---

## Testing

Beyond `contextLoads`, the tests that matter are the concurrency ones — and each was validated by **watching it fail first**.

A concurrency test that has never gone red is worthless: a green run is equally consistent with "the lock works" and "the test can't detect anything". So the lock was removed deliberately, the test was confirmed to catch the bug, and only then was the lock restored:

| Test | Invariant | Without the lock |
|---|---|---|
| `updateDoesNotResurrectDeletedTask` | after a `PUT`/`DELETE` race the store must be empty — either the update lands and the delete removes it, or the delete lands and the update 404s | failed on **round 1** — the delete reported success and the task came back carrying the update's edits |
| `concurrentCreatesOfSameTitleYieldExactlyOne` | 8 threads racing to create one title → exactly one wins, the rest get 409 | failed on **round 0** — **all 8** threads created the task; every one saw "no such title" before any inserted |

Neither race was rare. Both reproduced immediately, which is the point: on an in-memory map, a race isn't an unlucky edge case — it's the default outcome when threads start together.

Every endpoint was also driven against a running server and checked for the exact status code and headers. That caught a bug nothing else would have: `Location` was built with `URLEncoder`, which is *form* encoding, so `"buy milk"` became `buy+milk` — a literal plus in a URL path. The endpoint returned a perfectly correct 201 while handing the client a header that 404s. Only following the emitted `Location` reveals it; asserting the status never would.

---

## Known limitations

Honest scope, in rough priority order:

- **No persistence.** Tasks live in memory and vanish on restart. A `TaskRepository` interface behind the service is the natural next step, so swapping in Spring Data JPA wouldn't touch the controller.
- **No authentication or authorization.** Every caller can do everything.
- **Mutations are addressed by title, not id.** The id exists and is stable across renames, but isn't in any URL. Titles are human-typeable, which is nice — but a rename changes a task's URL, and `PUT` needs a collision check that an id-based route wouldn't. `PATCH /api/tasks/{id}` with title-based *search* is the more conventional design.
- **A task titled exactly `done` or `pending` is unreachable by title,** since those literal routes outrank the `{title}` pattern. Accepted knowingly rather than nesting the filters under `/status/`.
- **`synchronized` serialises all writes.** Irrelevant at this scale; the fix if it ever mattered is a lock per key or a repository whose store handles it.
- **No controller-layer tests.** Endpoint behaviour was verified by hand against a live server. `@WebMvcTest` with `MockMvc` would make that repeatable in CI.
- **No CI, no container.**
