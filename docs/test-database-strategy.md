# Test Database Strategy: H2 + MySQL → MySQL Only

**Status:** Decided
**Project:** llm-handoff (Spring Boot 3.5 backend)
**Date:** 2026-11-08

## Background

The backend test suite ran against two database engines:

- **Production and local development:** MySQL 8.0, managed by Flyway migrations (`backend/src/main/resources/db/migration/`).
- **Tests:** H2, an in-memory database running in `MODE=MySQL` compatibility mode, configured in `backend/src/test/resources/application.properties`.

H2's MySQL mode is an approximation. This means every Flyway migration effectively had to be valid on two different SQL dialects, even though only MySQL exists in production.

## Problem

The database-related failure is:

- `V3__add_git_sync_fields_to_projects.sql` adds five columns in a single `ALTER TABLE` statement. That syntax is valid MySQL but rejected by H2, so Flyway migration fails and `@SpringBootTest` classes (`ApplicationTests`, `SecurityIntegrationTest`) cannot load the application context.
- `V4__create_users_table.sql` uses the same multi-clause `ALTER` pattern (adds `user_id` plus its foreign key constraint) and would hit the same wall.

Fixing it on H2's terms would require rewriting both migrations into per-column statements. Because Flyway checksums applied migrations, editing V3/V4 would also require a `flyway repair` on production — a write to Flyway's bookkeeping on a live database to accommodate a database that does not exist in production.

The deeper issue is maintenance: every future migration would need to be authored, reviewed, and debugged for two dialects. H2's MySQL mode does not cover MySQL-specific features, so the compatibility surface keeps growing as the app grows.

## Decision

Tests run against real MySQL only. H2 is removed as a test dependency. Database-backed tests (`@SpringBootTest`, `@DataJpaTest`) use a disposable MySQL 8.0 container via Testcontainers, so the test database is the same engine and version as production.

## How it works

- Testcontainers starts a `mysql:8.0` container on a random port when a database-backed test context is needed; Spring's `@ServiceConnection` points the datasource at it. No credentials or URLs to manage.
- One container serves the whole `mvn test` run and is destroyed afterwards.
- `mvn test` works identically on a developer machine and in CI, with no MySQL service or schema setup — the only requirement is a Docker daemon.
- Pure unit tests (mocked services, `@WebMvcTest` slices) are unaffected and stay database-free.
- V3 and V4 are left untouched; no `flyway repair` on production is needed.

## Pros

- **One dialect.** Migrations only need to be valid on MySQL — the same engine they run on in prod. A class of "works here, fails there" test failures is eliminated.
- **Prod parity.** Schema, constraints, and behavior tested are exactly what prod runs. No surprises from compatibility-mode differences (column types, index behavior, constraint enforcement).
- **No prod bookkeeping change.** Avoids `flyway repair` on the production database.
- **Isolated and disposable.** Test DB is ephemeral, random-ported, and never touches the local `docker compose` MySQL or its data.

## Cons

- **Requires Docker wherever tests run.**
- **Slower than H2.** Container startup plus real MySQL boot adds several seconds per test run (amortized — one container per run, reused across classes).
- **Shared DB between tests.** All database-backed tests share one MySQL instance per run, so tests must isolate data (`@Transactional` rollback or explicit cleanup).
- **New dependency surface.** Testcontainers + the mysql/JUnit modules become test-scoped dependencies to keep updated.
- **Lost convenience.** H2 was zero-setup and instant. Anyone running the suite without Docker loses the ability to run the database-backed tests (pure unit tests still work).

## Why the maintenance tradeoff isn't worth it

Maintaining dual-dialect compatibility buys convenience but pays for it in three compounding ways:

1. Every migration is written twice in effect.
2. The failures are noisy and indirect.
3. It protects the wrong invariant.

For a single-MySQL production system, testing on a disposable copy of that same engine is strictly more honest than maintaining a second dialect forever; especially when Docker is already a required tool for this project.

## Scope of change

| Area | Change |
|---|---|
| `backend/pom.xml` | Remove `h2`; add `spring-boot-testcontainers`, `testcontainers:mysql`, `testcontainers:junit-jupiter` (test scope) |
| `backend/src/test/resources/application.properties` | Remove H2 datasource config; keep JWT/OAuth test values |
| New shared test config class | One `@ServiceConnection` `mysql:8.0` container declaration |
| `ApplicationTests`, `SecurityIntegrationTest` | Import the shared config |
| Migrations V3/V4 | **Unchanged** — no `flyway repair` needed |
| CI (GitHub Actions) | JDK 21 + `mvn -B test` in `backend/`; Docker already present on runners |
| Prod server | **No change** — Dockerfile builds with `-DskipTests`; test-scoped deps never ship |
| Local Mac | Docker must be running for `mvn test` (already required for `docker compose`) |
