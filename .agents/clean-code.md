# Clean Code Style

General code-quality rules for every service in this backend, on top of the
service-specific conventions in [`controller-style.md`](./controller-style.md),
[`mapper-style.md`](./mapper-style.md), and [`service-structure.md`](./service-structure.md).

## Rule 1: no magic numbers or magic strings

Any literal whose meaning isn't obvious from its value alone must be a named constant,
an enum, or a config property — never a bare literal repeated (or even used once) inline
in logic.

```java
// Bad — what is 30? what is "ACTIVE"? why 100?
if (user.getStatus().equals("ACTIVE") && daysSince(user.getLastLoginAt()) > 30) {
    ...
}
Pageable pageable = PageRequest.of(0, 100);

// Good
private static final int INACTIVE_THRESHOLD_DAYS = 30;
private static final int DEFAULT_PAGE_SIZE = 100;

if (user.getStatus() == UserStatus.ACTIVE && daysSince(user.getLastLoginAt()) > INACTIVE_THRESHOLD_DAYS) {
    ...
}
Pageable pageable = PageRequest.of(0, DEFAULT_PAGE_SIZE);
```

- Status/type-like strings (`"ACTIVE"`, `"PENDING"`) → a Java `enum`, not a `String`
  comparison, so the compiler catches typos and invalid values.
- Numbers that could plausibly change (page size, expiry duration, retry count) → either
  a `private static final` constant with a descriptive name, or an
  `application.yaml` property injected via `@Value`/`@ConfigurationProperties` if it's
  meant to be tunable per environment.
- `0`, `1`, `-1`, and `true`/`false` used structurally (loop counters, boolean flags) are
  not magic numbers — use judgment, don't turn every literal into a constant.

## Rule 2: comment the why, not the what — and know where it's worth it

The default is **no comment**. Code should read clearly enough from names and structure
that most lines need nothing added. Write a comment only where it earns its keep: it
tells the reader something the code cannot — a reason, a constraint, a non-obvious
consequence. A comment that just restates the line under it is noise, and noise is worse
than nothing because it still has to be read (and kept in sync) forever.

### 2a. Numbered steps in multi-step service methods

Service methods that do more than one thing (validate → fetch → transform → persist →
publish) should mark each step with a numbered comment. This makes the shape of the
method scannable without reading every line, and makes it obvious where a new step
should be inserted.

```java
public UserResponse createUser(CreateUserRequest request) {
    // 1. Validate business rule not covered by @Valid
    if (userRepository.existsByUsername(request.username())) {
        throw new DuplicateResourceException(IdentityErrorCode.USERNAME_ALREADY_EXISTS,
                "User", "username", request.username());
    }

    // 2. Map request to entity
    User user = userMapper.toEntity(request);
    user.setPasswordHash(passwordEncoder.encode(request.password()));

    // 3. Persist
    User saved = userRepository.save(user);

    // 4. Publish domain event
    userEventProducer.publishUserCreated(saved);

    // 5. Map to response
    return userMapper.toResponse(saved);
}
```

- Steps are numbered in execution order, restarting at `1` for each method.
- A one-line method (a single repository call, a single delegation) doesn't need this —
  reserve it for methods with real multi-step logic. Don't force a `// 1.` on a method
  that's just `return userRepository.findById(id)...`.
- The comment names *what* the step does, not a restatement of the code beneath it —
  `// 3. Persist` not `// 3. call save`.
- If a method needs more than ~6-7 steps, that's a signal it's doing too much — consider
  extracting a private method for a sub-group of steps instead of adding more numbers.

### 2b. Other places a comment is usually worth writing

Use `//` for a single-line comment. Once an explanation needs more than one line, switch
to a `/* ... */` block instead of stacking `//` on every line — it reads cleaner and
visually separates "this is one explanatory note" from a sequence of numbered-step
comments (2a), which stay single-line `//` even when there are several of them.

- **A business rule that isn't derivable from the code itself.** The code shows *that* a
  discount is capped at a value; it can't show *why* that cap exists (a legal/finance
  constraint, a decision from a specific meeting). One line above the check is enough:

  ```java
  // Promotion discount is capped at 50% per Finance policy (2026-01 pricing review)
  BigDecimal cappedDiscount = discount.min(price.multiply(MAX_DISCOUNT_RATIO));
  ```

- **A workaround for a specific bug or library quirk.** State what's being worked around
  and, if possible, a link/ticket, so a future reader knows when it's safe to remove. This
  usually runs more than one line, so it's a `/* */` block:

  ```java
  /*
   * Workaround: spring-cloud-gateway-server-webmvc 5.0.3 does not support the reactive
   * gateway's discovery.locator.enabled auto-routing; routes are declared explicitly
   * instead. Revisit if a future release adds it.
   */
  ```

- **Public API of `common-lib`.** Classes/methods consumed by every service but whose
  implementation the caller can't (and shouldn't need to) see benefit from a short
  Javadoc explaining the *contract* — see the existing Javadoc on
  `DuplicateResourceException`, `ExpiredException`, etc. as the reference style: one or
  two sentences, no restating of parameter names.

- **A non-obvious ordering/configuration dependency.** E.g. the `annotationProcessorPaths`
  order in each service's `pom.xml` (lombok → lombok-mapstruct-binding →
  mapstruct-processor) or the caveat on `GlobalExceptionHandler`'s security exception
  handlers (filter-chain rejections bypass `@ControllerAdvice`) — both are one-time
  "why is it in this order" facts that aren't visible from reading the config/code alone.

- **A deliberately deferred TODO.** Must state what's missing and why it's deferred, not
  just `// TODO`. A single-line `TODO` stays `//`; once it needs a second line to explain
  the consequence of not doing it yet, use a block:

  ```java
  /*
   * TODO(PROJ-482): add idempotency-key check once order-service exposes it; until then
   * a duplicate webhook retry can double-charge.
   */
  ```

None of these are mandatory boilerplate — write them only when the fact genuinely isn't
recoverable by reading the surrounding code.

## Rule 3: avoid N+1 queries

Never trigger a query inside a loop over query results. This includes implicit lazy-load
triggers (accessing a `@ManyToOne`/`@OneToMany` field on each entity in a loop), not just
explicit `repository.findById(...)` calls.

```java
// Bad — 1 query for orders, then N queries (one per order) to lazy-load items
List<Order> orders = orderRepository.findAll();
orders.forEach(order -> {
    int itemCount = order.getItems().size(); // triggers a SELECT per order
    ...
});
```

```java
// Good — one query with JOIN FETCH
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
List<Order> findAllWithItemsByStatus(@Param("status") OrderStatus status);
```

```java
// Good — @EntityGraph when the JPQL would otherwise get repetitive
@EntityGraph(attributePaths = "items")
List<Order> findByStatus(OrderStatus status);
```

```java
// Good — DTO projection when you don't need the full entity graph at all
@Query("SELECT new com.ecm.order.dto.response.OrderSummary(o.id, o.status, COUNT(i)) " +
       "FROM Order o JOIN o.items i GROUP BY o.id, o.status")
List<OrderSummary> findOrderSummaries();
```

- Prefer `JOIN FETCH` / `@EntityGraph` when you need the full related entity.
- Prefer a DTO projection (constructor expression or interface projection) when you only
  need a few fields — avoids loading entities you'll immediately discard.
- If you must batch-load lazily (e.g. dynamic association decided at runtime), fetch IDs
  once and load related entities with a single `findAllById(...)`/`IN` query instead of
  looping.
- Enable `spring.jpa.properties.hibernate.generate_statistics` (or log SQL) locally when
  writing a new list endpoint, and eyeball the query count for a page of results before
  considering the endpoint done.

## Also enforce (standard clean code, not unique to this project)

- **Guard clauses over nesting** — return/throw early instead of wrapping the rest of the
  method in an `if`.

  ```java
  // Bad
  public void cancel(Order order) {
      if (order.getStatus() == OrderStatus.PENDING) {
          // ... 20 lines of cancellation logic
      }
  }

  // Good
  public void cancel(Order order) {
      if (order.getStatus() != OrderStatus.PENDING) {
          throw new InvalidStateException("Order", order.getId(), order.getStatus().name(), "cancel");
      }
      // ... 20 lines of cancellation logic, un-indented
  }
  ```

- **Meaningful names** — no `data`, `obj`, `temp`, `list1`, single-letter variables
  outside a tiny loop index. A name should make a comment explaining it unnecessary.
- **Small methods** — a method should fit on one screen. If you need to scroll to see the
  whole thing, or the numbered-step comment list (Rule 2) goes past ~6-7 steps, extract a
  private method.
- **No commented-out code** — delete it; git history has it if it's ever needed again.

## What not to do

- Do not compare status/type fields as raw strings (`"ACTIVE"`, `"PENDING"`) — use an
  `enum`.
- Do not hardcode a tunable number (page size, timeout, retry count, threshold) inline —
  name it or externalize it to config.
- Do not add numbered step comments to trivial one-line methods just for the sake of it.
- Do not access a lazy association inside a loop over a query result — fetch it up front.
- Do not leave commented-out code in a commit.
- Do not write a comment that just restates the line beneath it in English
  (`// increment counter` above `counter++`, `// get user by id` above
  `userRepository.findById(id)`) — if the comment and the code say the same thing, delete
  the comment.
- Do not add a comment that duplicates what a well-named method/variable already
  communicates — if you need a comment to explain what `x` is, rename `x` instead.
- Do not add file/class header banners (`// ===== UserService =====`, author tags, blank
  copyright boilerplate) — git history already has authorship and dates.
- Do not leave a comment that no longer matches the code next to it after an edit — a
  wrong comment is actively worse than no comment because it misleads with confidence.
  When you change code with a comment attached, re-read the comment and update or delete
  it in the same change.
- Do not write bare `// TODO` with no context — see 2b for the minimum a TODO needs.
