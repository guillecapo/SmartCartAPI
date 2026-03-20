# AGENTS.md — SmartCart API

Context file for AI agents (GitHub Copilot, Claude, Cursor, etc.).
Read this before suggesting or generating any code for this project.

---

## Project Overview

SmartCart is a production-ready shopping cart API built with **Hexagonal Architecture (Ports & Adapters)** and **Domain-Driven Design** principles.

The most important rule: **the domain knows nothing about infrastructure.**

---

## Package Structure

com.msd.smartcart/

├── domain/          → Models, errors, ports — NO Spring, NO Redis, NO MongoDB

├── application/     → Use case orchestration — NO infrastructure imports

├── infrastructure/  → Adapters (HTTP, Redis, MongoDB, RabbitMQ) — Spring lives here

└── shared/          → Result, AppError, domain exceptions

---

## Non-Negotiable Architecture Rules

### 1. Domain isolation

❌ NEVER do this in domain/ or application/
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.jpa.repository.JpaRepository;

✅ Domain models are plain Java records
public record Cart(String cartId, String userId, List<CartItem> items) {}

### 2. Ports and Adapters — always program to interfaces

❌ NEVER inject concrete adapters
private final ActiveCartRepositoryAdapter redisAdapter;

✅ ALWAYS inject the port (interface)
private final ActiveCartRepository activeCartRepository;

### 3. Exception translation — adapters translate, services never see Spring exceptions

❌ NEVER let Spring/infrastructure exceptions reach the service layer
catch (DuplicateKeyException e) { ... }

✅ Adapters translate to domain exceptions defined in shared/exception/
catch (DuplicateKeyException e) {
throw new DuplicateOrderException();
}

Every adapter method MUST wrap all calls in try-catch blocks with at minimum:
- Specific catch for known infrastructure exceptions (e.g. RedisConnectionFailureException)
- Generic catch (Exception e) as last resort — always throws InfrastructureException, never swallows

✅ Correct adapter pattern
try {
redisTemplate.opsForValue().set(key, json, TTL);
} catch (RedisConnectionFailureException e) {
log.error("Redis unavailable [userId={}] — {}", userId, e.getMessage(), e);
throw new InfrastructureException("cart.redis.unavailable");
} catch (Exception e) {
log.error("Unexpected Redis failure [userId={}] — {}", userId, e.getMessage(), e);
throw new InfrastructureException("cart.redis.save.failed");
}

### 4. Error handling — use Result type, not exceptions

❌ NEVER throw business exceptions from services
throw new CartNotFoundException(userId);

✅ Return Result type for business errors
return Result.failure(CartError.NotFound.of(userId));

### 5. Failure behavior — respect criticality levels

Redis (active cart) — CRITICAL: abort on failure
try {
activeCartRepository.save(cart);
} catch (InfrastructureException e) {
return Result.failure(AppError.persistence("active.cart.infra.failed", userId));
}

MongoDB backup — NON-CRITICAL: best-effort, never abort main flow
try {
cartBackupRepository.saveOrUpdate(cart);
} catch (Exception e) {
log.warn("Backup failed (non-critical) — {}", e.getMessage());
}

RabbitMQ, AI recommendations — NON-CRITICAL: log and continue

### 6. switch on Exception — always rethrow on default

When using pattern matching switch on exceptions in services, the default branch
MUST rethrow — never return a silent Result.failure. Unknown exceptions are handled
by GlobalExceptionHandler, logged, and flagged for analysis.

❌ NEVER swallow unknown exceptions silently
default -> {
log.error("Unexpected error — {}", e.getMessage(), e);
yield Result.failure(AppError.persistence("unknown.error", userId)); // ← hides the problem
}

✅ ALWAYS rethrow on default — let GlobalExceptionHandler catch it
default -> {
log.error("Unexpected error — {}", e.getMessage(), e);
throw e;
}

### 7. Secrets — never hardcode, always environment variables with fallback

❌ NEVER hardcode secrets in any yml file
secret: my-hardcoded-secret-key

✅ ALWAYS use environment variable with explicit fallback
secret: ${JWT_SECRET:local-dev-fallback-key-minimum-256-bits}

This applies to ALL environments — local, test, prod. Consistency is non-negotiable.

---

## Domain Models — Key Decisions

| Model | ID Type | Reason |
|---|---|---|
| Order | ULID | Chronologically sortable, no separate createdAt needed |
| Cart | UUID | No chronological ordering needed |
| User | MongoDB ObjectId | Auth context only |

---

## Persistence Strategy

Redis  → source of truth for active carts, TTL 24h, key: cart:active:{userId}
MongoDB → backup only when totalValue > $500 OR itemCount > 20

Write order is non-negotiable:
1. Redis first — if fails, abort
2. MongoDB second — best-effort, if fails log and continue

---

## Security Context

- JWT authentication via JJWT — NOT OAuth2 Resource Server
- UserDetails mapping happens ONLY in UserDetailsServiceImpl — never in domain or application layers
- UserData is the domain transport object — no Spring Security imports outside infrastructure
- See ADR-001 in README for Spring Security coupling decision

---

## Idempotency

addItem and checkout require an Idempotency-Key header.
The IdempotencyStore caches responses in Redis — retries return cached response without re-executing.

Always check idempotency before executing:
CartResponse cached = idempotencyStore.get(idempotencyKey, CartResponse.class);
if (cached != null) return ResponseEntity.ok(cached);

---

## Logging Standards

Always use structured logging with context:
log.info("Action description [userId={}, cartId={}]", userId, cartId);
log.warn("Warning description [userId={}, reason={}]", userId, reason);
log.error("Error description [userId={}, cause={}]", userId, e.getMessage(), e);

Never log sensitive data — no passwords, no tokens, no full request bodies

---

## What NOT to Generate

- @Entity or JPA annotations — this project uses MongoDB and Redis
- Spring exceptions in domain or application layers
- Direct repository injection in controllers — always go through a service
- Business logic in adapters — adapters only translate, never decide
- @Transactional without explicit justification
- Checked exceptions in service methods — use Result type instead
- System.out.println — always use SLF4J logger
- Hardcoded secrets in any yml file — use environment variables with fallback
- Silent default branches in switch on Exception — always rethrow

---

## Checklist Before Submitting Code

- [ ] No Spring/infrastructure imports in domain/ or application/
- [ ] All repository access goes through a port interface
- [ ] Infrastructure exceptions translated in adapters before reaching services
- [ ] All adapter methods have try-catch covering InfrastructureException and generic Exception
- [ ] switch on Exception has default that rethrows, never yields silently
- [ ] No secrets hardcoded in any yml — environment variable with fallback
- [ ] Critical path failures return Result.failure() — best-effort failures log and continue
- [ ] New endpoints have @Operation, @ApiResponse and @Tag Swagger annotations
- [ ] Structured logs with relevant context fields
- [ ] No sensitive data logged