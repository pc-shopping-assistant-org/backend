# Backend — Agent Guide

This is the microservices backend for the PC Shopping Assistant project: an API gateway,
a Eureka discovery server, a shared `common-lib`, and 7 business services (identity,
catalog, order, payment, promotion, search, media). Spring Boot 4 / Java 25, single
Maven reactor rooted at this directory's `pom.xml`.

## Read this first

**[`./.agents/README.md`](./.agents/README.md)** indexes the coding conventions for this
backend: package structure, controller style, exception handling, MapStruct mappers, and
clean-code rules (magic numbers, comment policy, N+1 queries). Read it before writing or
reviewing code here — it encodes decisions already made for this project, so follow it
instead of re-deriving your own convention.

## Quick facts

- `ai-service/` is a Git submodule of `pc-shopping-assistant-org/ai-service`,
  using Python/FastAPI, LangGraph and Pydantic, outside the Maven reactor.
  Initialize it with `git submodule update --init --recursive`. Make AI changes
  in its repository, commit/push them there, then stage the updated gitlink here;
  never pin an unpublished AI commit when publishing the backend.
  Run `uv sync --frozen`, `uv run pytest -q`, `uv run ruff check src tests`, and
  `uv run mypy src` in that directory. Gateway routes `/api/v1/assistant/**`
  to its `/api/v1/**` API using `AI_SERVICE_URL`.

- Root `pom.xml` is the Maven reactor parent — it manages shared versions
  (`java.version`, `spring-cloud.version`, `mapstruct.version`,
  `lombok-mapstruct-binding.version`) for every module. Don't give a service module its
  own `spring-boot-starter-parent` parent.
- `common-lib` holds cross-cutting infrastructure only (`ApiResponse`, `PageResponse`,
  the shared exception types, `GlobalExceptionHandler`). Business-specific error codes
  and event/message payloads are intentionally duplicated per service, not centralized —
  see `.agents/exception-handling.md` and `.agents/service-structure.md`.
- `api-gateway` strips the service-name path prefix before forwarding, so a controller
  in `identity-service` maps to `/users`, reachable externally at
  `/identity-service/users`.
- Local dependencies: Postgres via `docker-compose.yml` (`docker compose up -d
  postgres`), Eureka at `:8761`, gateway at `:8080`.
