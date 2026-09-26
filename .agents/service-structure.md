# Service Package Structure

Recommended package layout for every business service in this backend
(`identity-service`, `catalog-service`, `order-service`, `payment-service`,
`promotion-service`, `search-service`, `media-service`). Package-by-**layer**, not
package-by-feature — each service is already a single bounded context, so an extra
feature-level split adds indirection without real benefit at this size. Pairs with
[`controller-style.md`](./controller-style.md), [`mapper-style.md`](./mapper-style.md),
and [`exception-handling.md`](./exception-handling.md).

## Layout

```
com.ecm.<service>/
├── <Service>Application.java
├── controller/
│   └── UserController.java
├── service/
│   └── UserService.java              # concrete class — no interface, see Rule 1
├── repository/
│   └── UserRepository.java           # Spring Data JPA interface
├── entity/
│   └── User.java                     # @Entity, never exposed outside the service
├── dto/
│   ├── request/
│   │   ├── CreateUserRequest.java
│   │   └── UpdateUserRequest.java
│   └── response/
│       └── UserResponse.java
├── mapper/
│   └── UserMapper.java               # MapStruct, see mapper-style.md
├── exception/
│   └── IdentityErrorCode.java        # see exception-handling.md
├── config/
│   ├── SecurityConfig.java
│   ├── KafkaConfig.java              # if this service uses Kafka
│   └── RabbitConfig.java             # if this service uses RabbitMQ
├── client/                           # only if this service calls another service
│   └── PaymentServiceClient.java
└── messaging/                        # only if this service publishes/consumes events
    ├── producer/
    │   └── OrderEventProducer.java
    ├── consumer/
    │   └── PaymentEventConsumer.java
    └── event/
        ├── OrderCreatedEvent.java
        └── PaymentCompletedEvent.java
```

`src/main/resources`:

```
resources/
├── application.yaml
└── db/migration/
    ├── V1__init_schema.sql
    └── V2__add_xxx_column.sql        # Flyway
```

`src/test/java` mirrors the same package structure as `src/main/java` (e.g.
`service/UserServiceTest.java` next to `service/UserService.java`).

## Rule 1: no interface for services — a concrete class is enough

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        return userMapper.toResponse(user);
    }
}
```

Do not create `UserService` (interface) + `UserServiceImpl` (implementation) unless
there's a real reason: more than one implementation at runtime, or a genuine need to mock
across a module boundary. In a single-implementation microservice this split is pure
ceremony — it doubles navigation for no benefit and doesn't meaningfully improve
testability (mock the concrete class directly instead).

## Rule 2: `client/` only exists when the service calls another service

If `order-service` calls `payment-service` over HTTP, that call is wrapped in a class
under `client/`, and failures are translated into `ExternalServiceException` (see
`exception-handling.md`) — never let a raw `RestClientException`/Feign exception escape
to the caller.

## Rule 3: `messaging/` only exists when the service publishes/consumes events

- `producer/` — classes that publish to Kafka/RabbitMQ.
- `consumer/` — classes annotated `@KafkaListener`/`@RabbitListener`.
- `event/` — the message payload classes.

If a single service uses **both** Kafka and RabbitMQ, split further by technology to
avoid mixing concerns:

```
messaging/
├── kafka/
│   ├── producer/
│   └── consumer/
└── rabbitmq/
    ├── producer/
    └── consumer/
```

If a service only uses one of the two, keep the flat `messaging/producer/` +
`messaging/consumer/` layout — don't add the technology-level split when there's nothing
to disambiguate.

## Rule 4: event payload classes are duplicated per service, not shared

When `order-service` publishes `OrderCreatedEvent` and `payment-service` consumes it,
each service declares its **own** copy of that class under its own `messaging/event/`
package, matching the agreed wire format — the same reasoning as
`exception-handling.md`'s per-service `ErrorCode`: sharing the class via `common-lib` (or
a dedicated contracts module) would couple every consumer's deployment to the
producer's, and this system prioritizes independent service deployability over that
type-safety guarantee.

To manage the resulting drift risk:

- Keep event payloads flat and simple (avoid deeply nested objects) — less structure
  means less to get out of sync.
- Version the topic/exchange name on breaking changes (`order.created.v2`) instead of
  mutating an existing event's shape — old consumers keep working against the old topic.
- A lightweight contract test (schema or Pact-style) between producer and consumer is
  worth adding later, but is not required to start.

## What not to do

- Do not create a `UserService` interface with a single `UserServiceImpl` — see Rule 1.
- Do not package by feature (`user/controller`, `user/service`, `order/controller`,
  `order/service`, ...) — this is a single bounded context per service, not a monolith
  that needs feature-level isolation.
- Do not add `client/` or `messaging/` packages to a service that doesn't call another
  service or use a message broker — empty ceremony packages add noise.
- Do not share event payload classes between producer and consumer services via
  common-lib or any other shared module.
