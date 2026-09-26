# Backend Agent Rules

Coding conventions for the `backend/` microservices reactor
(`discovery-server`, `api-gateway`, `common-lib`, and the 7 business services:
`identity-service`, `catalog-service`, `order-service`, `payment-service`,
`promotion-service`, `search-service`, `media-service`). Read these before writing or
reviewing code in this project — they encode decisions already made, so follow them
instead of re-deriving your own convention.

Read in this order the first time; after that, jump to whichever file covers what you're
touching.

| File | Covers |
|---|---|
| [`service-structure.md`](./service-structure.md) | Package layout for a business service (`controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `exception/`, `config/`, `client/`, `messaging/`) and when each package should or shouldn't exist. |
| [`controller-style.md`](./controller-style.md) | How to write REST controllers: thin controllers, constructor injection, `@Valid` DTOs, `ApiResponse` wrapping with explicit messages, HTTP status via `@ResponseStatus`/`ResponseEntity`, gateway path conventions. |
| [`exception-handling.md`](./exception-handling.md) | Where to throw (service layer, never controllers), `common-lib`'s exception types (`BusinessException`, `ResourceNotFoundException`, `DuplicateResourceException`, `ExternalServiceException`, `InvalidStateException`, `ExpiredException`), per-service `ErrorCode` enums, and `GlobalExceptionHandler` behavior. |
| [`mapper-style.md`](./mapper-style.md) | MapStruct setup (already wired in the root pom) and conventions for entity <-> DTO mappers, partial updates via `@MappingTarget`, list/page mapping. |
| [`clean-code.md`](./clean-code.md) | No magic numbers/strings, numbered step comments (`// 1. ...`) in multi-step service methods, avoiding N+1 queries, plus baseline clean-code habits (guard clauses, naming, method size). |

## Key cross-cutting decisions to know before you improvise

These come up repeatedly and are easy to get wrong by copying a pattern from a
different kind of project:

- **`common-lib` holds cross-cutting infrastructure only** (`ApiResponse`, `PageResponse`,
  `ErrorCode` interface + `CommonErrorCode`, the shared exception types,
  `GlobalExceptionHandler` auto-registered via Spring Boot auto-configuration). It does
  **not** hold business-specific error codes or event/message payload classes — those are
  intentionally duplicated per service to keep services independently deployable. See
  "Rule 2" in `exception-handling.md` and "Rule 4" in `service-structure.md`.
- **No `Service` interface + `ServiceImpl` split** — a concrete `@Service` class is
  enough unless there's a real second implementation.
- **The reactor root `pom.xml`** (`backend/pom.xml`) manages shared versions
  (`java.version`, `spring-cloud.version`, `mapstruct.version`,
  `lombok-mapstruct-binding.version`) and is the parent for every module. Don't
  reintroduce a direct `spring-boot-starter-parent` parent in a service's `pom.xml`.
- **`api-gateway` strips the service-name path prefix** before forwarding
  (`spring.cloud.gateway.server.webmvc.routes` + `StripPrefix`), so controllers map to
  the plain resource path (`/users`), not `/identity-service/users`.
