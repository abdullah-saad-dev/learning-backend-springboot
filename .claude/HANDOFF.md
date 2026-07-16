# Handoff — demo1 (Backend AI Engineering, Week 1)

**Last updated:** 2026-07-14

## Read this first: the working agreement

**Abdullah is doing coursework. He writes the code. You coach.**

He stopped a previous session mid-implementation with *"I wanna learn how to do it not you doing it."* Earlier in that session I had written the entire Week 1 assignment for him; it was deleted at his request and he rebuilt it himself, file by file.

**The loop that works — use it for every new piece of code:**

1. **Explain the concept and the machinery first**, not the syntax. He pushed back hard when I skipped this — *"I think you should explain the annotations and what we are actually doing rather than make me implement it blindly."* He was right. Once he had the DI container and the request pipeline in his head, he started *deriving* the annotations instead of recalling them.
2. **Brief him on any type or API before it appears in code he's asked to write.** See the calibration note below — this is the step I kept skipping.
3. **Show a worked example method that demonstrates it — on a *different* resource** (I used `/api/books`). This is what finally unstuck him on `ResponseEntity`, because *annotation placement is only obvious once you've seen it placed.* He translates the example rather than copying it.
4. **Then he writes the real method.** Review it, name what's wrong and *why*, hand it back. Don't fix it for him.

**Do not write Java for him**, even when it's faster. Plumbing he explicitly delegates is fair game: `pom.xml`/dependencies, build config, IDE config, tooling.

**Calibrating his level — read this carefully, I got it wrong.** He has a solid Java/OOP foundation, but that does **not** mean he knows every modern Java and Spring API. In his own words: *"it doesn't mean I memorize everything in normal java — I didn't know `record` at first glance, neither `ResponseEntity` and other things."* So:
- **Give a short brief on each new type/API before using it in an instruction** — `record`, `ResponseEntity`, `ProblemDetail`, `ConcurrentHashMap`, `Optional`, `UUID`, whatever comes next. Two sentences on what it is and why it's the right tool. Do not assume it's familiar just because it's "plain Java".
- Where he *did* get stuck repeatedly was **where annotations go and what they actually mean** (he wrote `public @Valid @RequestBody Note requestNote(String title)` — annotations on the method instead of the parameter). Explain the meaning conceptually, *then show the placement in an example*, then let him write it.
- The framework is the obstacle, but "it's just Java" is not a safe assumption either. When in doubt, brief him — it costs two sentences and it's what he asked for.

## Where the project stands

**Week 1 assignment ("Build your first API endpoint") — code complete, verified working end-to-end.**

The repo was wiped and regenerated from Spring Initializr at his request (there were no commits; nothing was lost). Stack: **Spring Boot 4.0.6, Java 25, Maven**. Still **zero commits** — nothing has been committed yet.

```
src/main/java/com/example/demo/
  Demo1Application.java          (Initializr scaffold)
  ApiExceptionHandler.java       @RestControllerAdvice → NoteNotFoundException = 404 ProblemDetail
  notes/
    Note.java                    record(id, title, createdAt)  — response type
    NoteRequest.java             record(@NotBlank title)       — request type
    NoteNotFoundException.java   RuntimeException, builds its message from the id
    NoteService.java             @Service over a ConcurrentHashMap, keyed by id
    NoteController.java          @RestController /api/notes, constructor-injected
src/main/resources/application.properties   (spring.mvc.problemdetails.enabled=true)
notes/spring-boot-week1-notes.html          his study reference — 12 topics
```

**Verified with real curl against the running app — all 8 cases pass:**
GET list → 200 `[]` · POST → 201 + `Location` · GET list → 200 array · GET by id → 200 · GET unknown id → 404 problem+json · POST blank title → 400 problem+json · DELETE → 204 · DELETE again → 404.

He chose the "honest delete" (404 on deleting a nonexistent id) over the idempotent 204, deliberately, and can justify it.

## What's left for the Week 1 submission

1. **`NoteControllerTest`** — not written yet. This is the immediate next task. `@WebMvcTest(NoteController.class)` + `@MockitoBean NoteService` + `MockMvc`, asserting the four behaviours (201+Location, 400 blank, 404 unknown, 200 array). He should write it; coach him through the slice-test concept.
2. **`api.http`** at the project root — he was about to create this (IntelliJ HTTP client; see gotcha #3).
3. **`README.md`** — what it is, how to run, a curl per endpoint, and *the layering rationale in his own words*. He earned that rationale; make sure it lands in the README, because it's what distinguishes his submission.
4. Cosmetic: reformat (`Ctrl+Alt+L`), a stray wildcard import.
5. Nothing is committed. Offer a first commit when he's ready.

## Gotchas already discovered (do not re-derive these)

1. **`@WebMvcTest` moved in Boot 4** → `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, *not* the `spring-boot-test-autoconfigure` package every online tutorial shows (Boot 3). Needs `spring-boot-starter-webmvc-test`, already in the pom. Verified by opening the jars.
2. **`-parameters` / the IntelliJ 500.** `@PathVariable String id` (unnamed) 500s when compiled without the `-parameters` javac flag. Maven sets it; IntelliJ's own compiler did not (its settings live in `.idea/`, which I deleted during the wipe — my fault). Two fixes were given: name it explicitly — `@PathVariable("id")` — and tick *Build Tools → Maven → Runner → "Delegate IDE build/run actions to Maven"*. **Confirm he applied both**; as of the last message he had been told but not yet verified.
3. **PowerShell `curl` is an alias for `Invoke-WebRequest`**, not curl. Every Unix curl example fails for him in confusing ways. Steer him to the `.http` file (no shell, no quoting) or `curl.exe`. Don't hand him bare `curl` commands.
4. **Trailing slash**: `@RequestMapping("/api/notes/")` makes `GET /api/notes` a 404 in Boot 3+. He hit this; it's fixed.
5. **Orphan JVMs on port 8080.** Stopping a `spring-boot:run` background task does *not* kill the forked JVM. If a request returns something impossible, check `netstat -ano | grep :8080` — you may be talking to a stale process running old code. This wasted time twice.
6. `.idea/misc.xml` was hand-written to restore the project SDK after the wipe. If the IDE misbehaves, re-import the Maven project rather than hand-editing further.

## The bigger arc

He asked whether to fight the program's JS/TS material (React Flow, Inngest, Next.js) with his Java background. Conclusion, and he agreed: **do the backend assignments in Spring Boot, learn just enough JS for the few assignments that genuinely require it** (JavaScript 101, the three `[AP]` React Flow parts, a thin capstone frontend). Of ~20 assignments, only 3–4 actually need JavaScript.

A full week-by-week Java/Spring mapping of the 10-week roadmap is in the plan file:
`C:\Users\Admin\.claude\plans\i-m-now-having-to-wiggly-bonbon.md`
(also exported as a PDF to `C:\Users\Admin\Downloads\backend-ai-java-plan.pdf` — he finds long docs painful to read in the terminal and prefers a rendered file).

**Open action item he should not skip:** message his track lead to confirm Spring Boot submissions are accepted, especially for the capstone. Unclear whether he's done this.

**Next up (Week 2):** Containerize your stack (Docker, language-agnostic — a multi-stage Dockerfile + compose with Postgres), and JavaScript 101.
