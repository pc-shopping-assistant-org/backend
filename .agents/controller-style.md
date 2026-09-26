# Controller Style

How to write REST controllers across all services in this backend. Applies to every
service module (`identity-service`, `catalog-service`, `order-service`, ...). Pairs with
[`exception-handling.md`](./exception-handling.md) — read that first for how errors flow
out of the service layer.

## Rule 1: controllers are thin — no business logic, no try/catch

A controller only: validates input, delegates to a service method, wraps the result.
Business rules, persistence, and exception throwing all live in the service layer. Never
`try/catch` a business exception in a controller — let it propagate to
`GlobalExceptionHandler` (see `exception-handling.md`).

## Rule 2: constructor injection, not field injection

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor   // Lombok — generates the constructor for all final fields
public class UserController {

    private final UserService userService;
    // ...
}
```

Never `@Autowired` on a field. Fields are `private final`.

## Rule 3: dedicated request/response DTOs, never expose JPA entities

Request/response shapes live in `com.ecm.<service>.dto`, separate from `@Entity` classes.
Never return an entity directly from a controller (leaks internal fields, breaks on lazy
associations, couples the API contract to the DB schema).

## Rule 4: validate with `@Valid` + Bean Validation, not manual checks

```java
public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) { ... }
```

Field-level rules (`@NotBlank`, `@Email`, `@Size`, ...) go on the DTO. Field errors are
caught automatically by `GlobalExceptionHandler` — do not write manual `if (x == null)
throw ...` for basic input shape checks.

## Rule 5: every response is wrapped in `ApiResponse`, with an explicit message

Always call the `ApiResponse.success(message, data)` overload with a specific,
action-describing message — not the message-less `success(data)` overload, which falls
back to the generic `"Success"`. A good message tells the caller what just happened
without them needing to infer it from the HTTP method + path.

```java
return ApiResponse.success("User created successfully", userService.createUser(request));
return ApiResponse.success("Get user successfully", userService.getById(id));
return ApiResponse.success("Get users successfully", PageResponse.of(userService.search(pageable)));
```

Lists/pages are always wrapped through `PageResponse.of(page)` — never return a raw
`Page<T>` or `List<T>`.

## Rule 6: set the right HTTP status

- Fixed status regardless of outcome → `@ResponseStatus` on the method (cleaner than
  wrapping in `ResponseEntity`):

  ```java
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
      return ApiResponse.success("User created successfully", userService.createUser(request));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
      userService.delete(id);
  }
  ```

- `GET`/`PUT`/`PATCH` need no annotation — the Spring MVC default of `200 OK` is already
  correct.
- Only reach for `ResponseEntity<...>` when the status must be decided at runtime (e.g.
  an upsert endpoint returning `200` vs `201` depending on whether the resource already
  existed) or when you need to set a response header (e.g. `Location`).
- `204 No Content` never has a response body — the method returns `void`, not
  `ApiResponse<Void>`.

## Rule 7: path mapping has no service-name prefix

`api-gateway` strips the `/{service-name}` prefix before forwarding (see
`StripPrefix` filter in `api-gateway`'s routes), so controllers map to the plain resource
path:

```java
@RequestMapping("/users")   // correct — reachable via gateway at /identity-service/users
```

Not `@RequestMapping("/identity-service/users")`.

## Full example

```java
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("User created successfully", userService.createUser(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success("Get user successfully", userService.getById(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success("Get users successfully", PageResponse.of(userService.search(keyword, pageable)));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("User updated successfully", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }
}
```

Response shape for a success case (`ApiResponse.success(message, data)`):

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Get user successfully",
  "data": { "id": "...", "username": "test" },
  "timestamp": "2026-09-27T03:20:00Z"
}
```

Error responses are entirely handled by `GlobalExceptionHandler` — see
`exception-handling.md` for the shape of `code`/`message` on failure.

## What not to do

- Do not put `try/catch` for business errors in a controller.
- Do not return a JPA entity directly from an endpoint.
- Do not use the message-less `ApiResponse.success(data)` when a more specific message is
  available — bland `"Success"` messages make API responses harder to read at a glance.
- Do not return raw `Page<T>`/`List<T>` for paginated data — always wrap with
  `PageResponse.of(...)`.
- Do not hardcode the service name in the request mapping path.
- Do not use field injection (`@Autowired` on a field).
