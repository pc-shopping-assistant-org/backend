# Mapper Style (MapStruct)

How to map between JPA entities and DTOs across all services in this backend. Applies to
every business service module (`identity-service`, `catalog-service`, `order-service`,
`payment-service`, `promotion-service`, `search-service`, `media-service`). Pairs with
[`controller-style.md`](./controller-style.md) (DTOs never expose entities) and
[`exception-handling.md`](./exception-handling.md).

## Setup (already wired, don't redo)

`mapstruct` + `mapstruct-processor` are version-managed in the root `pom.xml`
(`mapstruct.version`, `lombok-mapstruct-binding.version`). Each business service already
declares:

- a compile dependency on `org.mapstruct:mapstruct`
- an `annotationProcessorPaths` list on `maven-compiler-plugin` with, in this exact order:
  `org.projectlombok:lombok` → `org.projectlombok:lombok-mapstruct-binding` →
  `org.mapstruct:mapstruct-processor`

The order matters: Lombok must generate getters/setters before MapStruct reads them, and
`lombok-mapstruct-binding` makes the two annotation processors cooperate instead of
running in an undefined order. If you scaffold a new service module, copy this block from
an existing service's `pom.xml` rather than re-deriving it.

`common-lib`, `discovery-server`, and `api-gateway` do not need MapStruct (no
entities/DTOs to map).

## Rule 1: one mapper interface per aggregate, `componentModel = "spring"`

```java
package com.ecm.identity.mapper;

import com.ecm.identity.dto.UserResponse;
import com.ecm.identity.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
```

`componentModel = "spring"` makes MapStruct generate `UserMapperImpl` annotated
`@Component`, so it's injected like any other Spring bean via constructor injection — no
manual `Mappers.getMapper(...)` calls.

## Rule 2: mappers only convert shapes, they never contain business logic

A mapper method copies/renames/reformats fields. It does not query a repository, call
another service, throw a `BusinessException`, or apply a business rule. If a field needs
computed data (e.g. a derived total, a lookup by ID), compute it in the service layer and
pass it in, or use an `@AfterMapping`/default method only for pure formatting (date
formatting, enum-to-string, etc.) — never for anything that can fail or has side effects.

## Rule 3: name mapping methods by direction, not generically

```java
public interface UserMapper {
    UserResponse toResponse(User user);          // entity -> response DTO
    User toEntity(CreateUserRequest request);     // request DTO -> new entity
    void updateEntity(@MappingTarget User user, UpdateUserRequest request); // partial update
}
```

Avoid a single overloaded `map(...)` name — the direction should be obvious from the
method name without reading the signature.

## Rule 4: use `@Mapping` for field name/type mismatches, don't hand-write those methods

```java
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "totalAmount", source = "total")
    OrderResponse toResponse(Order order);
}
```

If a mapper needs more than 2-3 `@Mapping` overrides or nested object flattening, that's
fine — that's exactly what MapStruct is for. Reach for a hand-written method only when the
transformation isn't a structural mapping (e.g. calling another service).

## Rule 5: partial updates use `@MappingTarget`, not a full re-create

```java
@Mapping(target = "id", ignore = true)
void updateEntity(@MappingTarget User user, UpdateUserRequest request);
```

```java
// in the service
User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
userMapper.updateEntity(user, request);
userRepository.save(user);
```

This mutates the managed entity in place (correct for JPA dirty checking) instead of
constructing a new entity and losing fields the request didn't include.

## Rule 6: list/page mapping is automatic — don't hand-write loops

MapStruct generates `List<UserResponse> toResponseList(List<User> users)` for free from
the single-object method signature — declare it once and MapStruct infers it:

```java
List<UserResponse> toResponseList(List<User> users);
```

Combine with `PageResponse.of(...)` in the service layer:

```java
Page<User> page = userRepository.findAll(pageable);
List<UserResponse> content = userMapper.toResponseList(page.getContent());
return new PageImpl<>(content, pageable, page.getTotalElements());
```

## What not to do

- Do not call `Mappers.getMapper(UserMapper.class)` manually — always inject via
  constructor (`componentModel = "spring"` already makes it a bean).
- Do not put repository calls, external service calls, or exception throwing inside a
  mapper method.
- Do not return an entity from a mapper meant to produce a response DTO, or vice versa.
- Do not write manual field-by-field copy code (`target.setX(source.getX())`) when a
  MapStruct mapper can do it — that's the whole point of using it.
- Do not forget `@MappingTarget` for partial updates — re-creating the entity loses JPA
  identity/dirty-checking and can accidentally null out fields the request didn't send.
