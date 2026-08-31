# AGENTS.md

## Purpose

- This file defines the working agreement for AI-assisted pair programming in this repository.
- Treat the user as the decision owner. Explain material trade-offs, then implement the requested outcome autonomously when the scope is clear.
- Communicate progress, findings, and final results in Korean unless the user requests another language.
- Prefer small, reviewable changes that preserve the existing architecture and business invariants.

## Project context

- This is a single-module e-commerce backend built with Java 21, Spring Boot, and Gradle.
- The base package is `com.project.eshop_refact`.
- The main engineering concerns are stock consistency, order idempotency, concurrency control, query availability, cache consistency, and deep-pagination performance.
- MySQL is the production-style database. Redis is used for caching, distributed locks, idempotency, waiting queues, and ShedLock.
- JPA/Hibernate, QueryDSL, Flyway, Spring Security, JWT, Actuator, and SpringDoc are part of the current stack.

## Repository map

- `src/main/java/com/project/eshop_refact/domain`: order, product, user, and queue domain code.
- `src/main/java/com/project/eshop_refact/global`: shared configuration, security, exceptions, interceptors, and response types.
- `src/main/resources/db/migration`: versioned Flyway migrations.
- `src/test/java/.../controller`: Spring MVC slice tests.
- `src/test/java/.../service` and `.../domain`: unit and domain tests.
- `src/test/java/.../integration`: H2/Redis-backed integration and concurrency tests.
- `src/test/java/.../stressTest`: local data and load-test preparation utilities; these are not ordinary unit tests.

## Pair-programming workflow

1. Inspect the relevant production code, tests, configuration, and `git status` before editing.
2. State assumptions when requirements are ambiguous, but do not block on low-risk details that can be inferred from the repository.
3. For a bug, identify the cause and add or update a regression test when practical.
4. Implement the smallest cohesive change that solves the requested problem.
5. Run the narrowest relevant tests first, then broaden verification in proportion to risk.
6. Review the final diff for unrelated edits, secrets, generated files, and accidental data changes.
7. Report changed behavior, validation performed, failures or skipped checks, and remaining risks.

## Change discipline

- Preserve pre-existing user changes in a dirty worktree. Do not restore, stage, or commit unrelated files.
- Do not perform unrelated refactors, mass formatting, package moves, or dependency upgrades.
- Use the Gradle wrapper (`./gradlew`), not a system Gradle installation.
- Ask before adding a production dependency when the requirement can materially affect architecture or operations.
- Never edit generated QueryDSL sources under `build/generated/querydsl`.
- Never edit or commit runtime data under `mysql-data/`.
- Do not commit `build/`, IDE metadata, `.DS_Store`, `__pycache__/`, logs, tokens, or local secret files.
- Do not commit or push unless the user explicitly asks. When asked, commit only task-related paths and report the commit and target branch.

## Java and build configuration

- Java 21 is the project baseline.
- When changing the Java baseline, keep these files synchronized:
  - `build.gradle`
  - `Dockerfile`
  - `.github/workflows/deploy.yml`
  - `README.md`
- Preserve Gradle toolchain usage so compilation and tests use the declared JDK.
- Use constructor injection. Follow existing Lombok usage unless the touched code has a reason not to.
- Follow the existing Java formatting and package conventions; avoid wildcard imports.

## Domain and transaction invariants

- Service classes should default to `@Transactional(readOnly = true)` where appropriate. Mark state-changing methods with `@Transactional`.
- Keep entity state changes inside domain methods instead of exposing public setters.
- Stock must never become negative, and concurrent order attempts must not oversell inventory.
- Keep distributed-lock acquisition outside the database transaction so threads waiting for Redis do not occupy database connections.
- Release a Redisson lock in `finally` only when it was acquired and is held by the current thread.
- When catching `InterruptedException`, restore the interrupt flag with `Thread.currentThread().interrupt()`.
- Preserve waiting-queue cleanup semantics: only remove users when the corresponding lock-processing path owns that responsibility.
- Validate order ownership before cancellation or other user-scoped state changes.

## Idempotency and cache consistency

- Order idempotency must use an atomic Redis operation such as `setIfAbsent` to claim a key.
- A completed idempotent request must return the stored response instead of creating another order.
- On order-processing failure, clear the processing key so a safe retry is possible.
- Keep idempotency keys scoped by user and request key, with explicit TTLs.
- Do not evict product cache entries before a database transaction commits.
- Preserve the `ProductCacheEvictEvent` plus `@TransactionalEventListener(AFTER_COMMIT)` pattern unless a replacement provides the same rollback safety.

## API, security, and errors

- Keep controllers thin; business decisions belong in services or domain objects.
- Use request/response DTOs at API boundaries rather than exposing entities directly.
- Use the existing `ApiResponse`, `ErrorResponse`, `BusinessException`, and `ErrorCode` conventions.
- Preserve authentication and authorization checks when modifying controller routes or service methods.
- Never add real database passwords, JWT secrets, tokens, Docker credentials, or deployment keys to tracked files.
- Configuration examples must use placeholders or environment-variable references.

## Persistence, QueryDSL, and Flyway

- Keep Flyway as the schema owner for MySQL and Hibernate `ddl-auto` as `validate` in local/production-style profiles.
- Add schema changes as a new migration such as `V2__add_order_index.sql`.
- Do not modify an already-applied versioned migration. Roll forward with a new migration.
- Do not use `ddl-auto: update`, `create`, or `create-drop` for local or production schema evolution.
- Use H2 `create-drop` only for isolated test configuration.
- Avoid collection fetch joins together with pageable queries because they can trigger in-memory pagination.
- When changing no-offset pagination, verify cursor direction, deterministic ordering, boundary behavior, and supporting indexes.
- When changing a query for performance, inspect both result correctness and query count/shape.

## Test strategy

### Fast feedback

- Run the tests nearest to the changed code first.
- Tests that normally do not require live Redis or MySQL can be selected with:

```bash
./gradlew test \
  --tests 'com.project.eshop_refact.domain.*' \
  --tests 'com.project.eshop_refact.service.*' \
  --tests 'com.project.eshop_refact.controller.*' \
  --tests 'com.project.eshop_refact.config.*'
```

### Integration and concurrency tests

- Tests using the full Spring context may require Redis at `localhost:6379`; test data otherwise uses H2 unless a local profile is explicitly active.
- Start only the required Redis service when integration verification needs it:

```bash
docker compose up -d redis
```

- Do not describe a Redis connection failure as a Java or application-code compatibility failure.
- For concurrency-test failures, investigate lock scope, transaction boundaries, shared-state cleanup, and executor completion before changing timeouts or thread counts.
- Do not weaken assertions, reduce concurrency, or add sleeps merely to make a flaky test pass.

### Stress-test utilities

- Classes under `src/test/java/.../stressTest` use the `local` profile and can write substantial data to the developer's MySQL database.
- Do not run stress-test utilities unless the user explicitly requests load-test preparation or approves the database mutation.
- Do not run `./gradlew clean test` blindly when local MySQL or Redis state is unknown, because it includes these classes.

### Build verification

- Use these commands when their scope matches the change:

```bash
./gradlew classes
./gradlew bootJar -x test
```

- If a required service is unavailable, still run safe compilation or packaging checks and clearly report the infrastructure blocker.

## Git and delivery

- Before committing, run `git diff --check` and inspect the exact task-related diff.
- Use a concise commit message that describes the behavior or maintenance outcome.
- Never include unrelated staged changes merely because they were already in the index.
- Do not rewrite history, force-push, or delete branches unless the user explicitly requests it.
- A completion report must include:
  - the implemented outcome;
  - key files changed;
  - tests and build commands run;
  - any failures, skipped checks, or external-service requirements;
  - the commit hash and pushed branch when a push was requested.
