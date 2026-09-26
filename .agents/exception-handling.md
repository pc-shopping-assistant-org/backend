# Exception Handling Style

How to throw and handle errors across all services in this backend. Applies to every
service module (`identity-service`, `catalog-service`, `order-service`, ...).

## Building blocks (from `common-lib`)

- `com.ecm.common.exception.ErrorCode` — interface (`getCode()`, `getDefaultMessage()`,
  `getHttpStatus()`). Do not throw raw strings or HTTP status codes directly.
- `com.ecm.common.exception.CommonErrorCode` — enum implementing `ErrorCode` for
  cross-cutting cases shared by every service: `VALIDATION_ERROR`, `BAD_REQUEST`,
  `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.
- `com.ecm.common.exception.BusinessException` — the exception type to throw for any
  expected business-rule failure. Constructed with an `ErrorCode` (+ optional message
  override).
- `com.ecm.common.exception.ResourceNotFoundException` — convenience subclass of
  `BusinessException` pre-wired to `CommonErrorCode.NOT_FOUND`. Use for "X not found".
- `com.ecm.common.exception.DuplicateResourceException` — counterpart for "X already
  exists" conflicts (duplicate username, SKU, promotion code, ...). Defaults to
  `CommonErrorCode.CONFLICT`, or accepts a service-specific `ErrorCode` when the exact
  code matters to API consumers.
- `com.ecm.common.exception.ExternalServiceException` — wraps a failed call to another
  service (timeout, connection refused, 5xx response). Use this at the boundary where a
  service calls another one (RestTemplate/Feign/WebClient) instead of letting the raw
  HTTP client exception leak out. Maps to `CommonErrorCode.SERVICE_UNAVAILABLE`.
- `com.ecm.common.exception.InvalidStateException` — a state-machine violation: the
  operation can't be performed given the resource's current state (cancelling a shipped
  order, refunding an uncaptured payment, ...). Maps to `CommonErrorCode.INVALID_STATE`.
- `com.ecm.common.exception.ExpiredException` — a resource that existed but is no longer
  usable because it expired (OTP, auth token, promotion code, payment session, ...). Maps
  to `CommonErrorCode.EXPIRED` (HTTP 410).
- `com.ecm.common.exception.GlobalExceptionHandler` — `@RestControllerAdvice` registered
  automatically via Spring Boot auto-configuration (no per-service wiring needed). Catches
  `BusinessException`, `MethodArgumentNotValidException`, `ConstraintViolationException`,
  Spring Security's `AccessDeniedException`/`AuthenticationException` (thrown from inside
  controller/service code, e.g. `@PreAuthorize` — filter-chain rejections are handled by
  Security's own entry points, not this class), and a generic `Exception` fallback. Formats
  every error as `ApiResponse`.

Every service already gets this for free by depending on `common-lib` — do not write a
second `@RestControllerAdvice` in a service.

## Rule 1: throw at the service/domain layer, never in controllers

Controllers only translate HTTP <-> DTO and delegate. They must not contain
`try/catch` for business errors — let exceptions propagate to `GlobalExceptionHandler`.

```java
// Controller — no try/catch
@PostMapping
public ApiResponse<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
    return ApiResponse.success(userService.createUser(request));
}

// Service — this is where business rules are validated and exceptions are thrown
public UserDto createUser(CreateUserRequest request) {
    if (userRepository.existsByUsername(request.username())) {
        throw new BusinessException(IdentityErrorCode.USERNAME_ALREADY_EXISTS);
    }
    ...
}
```

## Rule 2: one `ErrorCode` enum per service for domain-specific errors

Do not add service-specific errors (e.g. "SKU already exists", "insufficient balance")
to `CommonErrorCode` in `common-lib`. Instead, each service defines its own enum
implementing `ErrorCode`, placed at `com.ecm.<service>.exception.<Service>ErrorCode`.

```java
package com.ecm.identity.exception;

public enum IdentityErrorCode implements ErrorCode {
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    // ...
}
```

Only add to `CommonErrorCode` when the error is truly cross-cutting (used by 2+ services
or by shared infrastructure). Changing `common-lib` requires rebuilding/reinstalling it
for every consuming service, so keep it stable.

## Rule 3: never leak infrastructure exceptions to the caller

Catch persistence/integration exceptions (`DataIntegrityViolationException`,
`SQLException`, Feign/HTTP client errors, etc.) at the service layer boundary and
re-throw as a `BusinessException` with the appropriate `ErrorCode`. Do not let JPA/JDBC
exceptions bubble up to the controller — they'll be caught by the generic handler and
reported as an opaque `INTERNAL_ERROR`, hiding the real cause from the API consumer.

```java
try {
    orderRepository.save(order);
} catch (DataIntegrityViolationException ex) {
    throw new BusinessException(OrderErrorCode.DUPLICATE_ORDER, ex.getMessage());
}
```

## Rule 4: use Bean Validation for input validation, not manual checks

Annotate request DTOs with `jakarta.validation` annotations (`@NotNull`, `@Size`,
`@Email`, ...) and `@Valid` on controller method parameters. Field-level errors are
automatically caught by `GlobalExceptionHandler` and returned as a structured
`ApiResponse` with a `field -> message` map — no manual `if (field == null) throw ...`
for basic input shape checks.

## Rule 5: use the right exception subclass for the shape of the error

```java
// not found
User user = userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("User", id));

// already exists — generic CONFLICT code
if (userRepository.existsByUsername(username)) {
    throw new DuplicateResourceException("User", "username", username);
}

// already exists — service-specific code (preferred when the client needs to
// distinguish this from other conflicts)
if (userRepository.existsByUsername(username)) {
    throw new DuplicateResourceException(IdentityErrorCode.USERNAME_ALREADY_EXISTS,
            "User", "username", username);
}

// calling another service
try {
    paymentClient.charge(request);
} catch (RestClientException ex) {
    throw new ExternalServiceException("payment-service", ex);
}

// state-machine violation
if (order.getStatus() != OrderStatus.PENDING) {
    throw new InvalidStateException("Order", order.getId(), order.getStatus().name(), "cancel");
}

// expired resource
if (otp.getExpiresAt().isBefore(Instant.now())) {
    throw new ExpiredException("OTP", otp.getId());
}
```

For every other business-rule violation, throw `BusinessException` with a specific
`ErrorCode` — do not reuse `NOT_FOUND`/`CONFLICT` as a generic catch-all when a more
precise service-specific code exists or should be added.

## What not to do

- Do not throw raw `RuntimeException`, `IllegalStateException`, or `IllegalArgumentException`
  for business errors — they are only caught by the generic fallback handler and reported
  as an unhelpful `INTERNAL_ERROR`.
- Do not write a second `GlobalExceptionHandler`/`@ExceptionHandler` at the service level
  for cases already covered by common-lib.
- Do not add service-specific error codes to `common-lib`'s `CommonErrorCode`.
- Do not swallow exceptions silently (empty `catch` blocks) — either translate to a
  `BusinessException` or let it propagate.
