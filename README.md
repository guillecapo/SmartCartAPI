# SmartCart API

Production-ready shopping cart API built with **Hexagonal Architecture**, **Domain-Driven Design** principles and **Spec-Driven Development** — the API contract is defined via OpenAPI 3.0 and served through Swagger UI, ensuring the spec is the single source of truth for all consumers.

---

## 📖 API Specification

Once running, the full interactive API spec is available at:

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

All endpoints, request/response schemas, error codes and idempotency contracts are documented in the spec.

---

## 🏗️ Architecture & Design

Built following **Ports & Adapters (Hexagonal Architecture)** — the domain is completely isolated from infrastructure. Swapping Redis for Memcached or MongoDB for PostgreSQL only touches the adapter layer.

Key engineering decisions documented as **Architecture Decision Records (ADRs)**:
- Dual persistence strategy (Redis + MongoDB) with explicit threshold-based backup
- Lazy stock validation with auto-correction at checkout
- ULID for chronologically sortable order IDs
- Idempotency keys on mutating operations
- JWT authentication with conscious Spring Security coupling — pragmatic decision documented and justified

---

## ⚙️ Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (records, sealed interfaces, pattern matching) |
| Framework | Spring Boot 3.3 |
| Active carts | Redis — source of truth, TTL 24h |
| Persistence | MongoDB — cart backup + orders |
| Messaging | RabbitMQ — event-driven notifications with DLQ |
| Security | Spring Security + JWT (JJWT) |
| API Spec | OpenAPI 3.0 / Swagger UI (springdoc) |
| Infrastructure | Docker Compose |

---

## 🎯 Production-Ready Patterns

- **Spec-Driven Development** — OpenAPI contract defined and served via Swagger UI
- **Idempotency** — prevents duplicate cart operations and orders on client retries
- **Best-effort pattern** — MongoDB backup and AI recommendations degrade gracefully without aborting the main flow
- **Dead Letter Queue** — failed order notifications persist for reprocessing with exponential backoff (1s → 2s → 4s)
- **Auto-correction at checkout** — out-of-stock items removed automatically, user notified with full detail
- **Fail-fast on critical path** — Redis failures abort immediately, MongoDB backup failures are non-critical
- **Result type** — railway-oriented error handling, no unchecked exceptions leaking across layers

---

## 📐 Key Architectural Decisions

**Why Redis + MongoDB dual persistence?**
Losing a $10 cart is acceptable. Losing an $800 cart is not. Redis handles all active carts in O(1) with native TTL. MongoDB backs up only carts exceeding $500 or 20 items.

**Why lazy stock validation?**
Inventory changes between add-to-cart and checkout anyway. Validating at add-item adds latency with no real guarantee — validate once at checkout and auto-correct.

**Why ULID for order IDs?**
Chronological ordering without a separate `createdAt` field or additional MongoDB index. Lexicographically sortable by design.

**Why RabbitMQ over Kafka?**
Single consumer, no replay needed. RabbitMQ + DLQ is operationally simpler and sufficient. Kafka would be correct if multiple services needed to consume the same event.

---

## 🚀 Running locally

**1. Start infrastructure**
```bash
docker-compose up -d
```

**2. Seed products**
```bash
# PowerShell
Get-Content scripts/seed-products.js | docker exec -i smartcart-mongodb mongosh

# CMD
docker exec -i smartcart-mongodb mongosh < scripts/seed-products.js
```

**3. Run the app**
```bash
./gradlew bootRun
```

**4. Open Swagger UI**
```
http://localhost:8080/swagger-ui.html
```

Register at `POST /auth/register`, copy the token, click **Authorize** and start testing. 🚀

---

## 📋 Documented Technical Debt

All known limitations are explicitly documented — orphaned carts on failed `clearCart`, TTL-expired carts below backup threshold, and missing `findByCartId` index in Redis. Engineering is about knowing what you're trading off, not pretending trade-offs don't exist.

---

## 🏛️ Architecture Decision Records

### ADR-001: Spring Security como framework de autenticación y autorización

**Fecha:** 2025
**Estado:** Aceptado

#### Contexto
`AuthService` en la capa de aplicación utiliza directamente `AuthenticationManager` y `PasswordEncoder` de Spring Security, acoplando la capa de aplicación al framework de seguridad. Se evaluó si era necesario abstraer estas dependencias detrás de ports propios siguiendo el patrón de arquitectura hexagonal del proyecto.

#### Alternativas evaluadas
- **Ports propios** (`AuthenticatorPort`, `PasswordEncoderPort`) — desacopla completamente la capa de aplicación de Spring Security
- **Mantener Spring Security directo** — acoplamiento pragmático justificado por contexto

#### Decisión
Se mantiene Spring Security directamente en `AuthService` sin abstracción de ports.

#### Justificación
- Spring Security es un framework robusto y popular con gran soporte y respaldo de la comunidad, lo que lo convierte en una elección pragmática y respaldada para el proyecto
- Implementación conocida por el equipo, reduce curva de aprendizaje y tiempo de entrega
- `AuthenticationManager` y `PasswordEncoder` son interfaces abstractas de Spring — no clases concretas
- Los frameworks alternativos (Apache Shiro, Quarkus Security, Micronaut Security) trabajan con modelos distintos, por lo que los ports propios no garantizarían portabilidad real sin reescritura significativa
- Se considera **inmutabilidad práctica** en el uso de Spring Security dado su robustez y adopción en el ecosistema Java empresarial

#### Consecuencias
- `AuthService` importa `org.springframework.security.*` — acoplamiento consciente y documentado
- Si en el futuro se requiere cambiar el framework de seguridad, se debe analizar el nuevo modelo de autenticación antes de asumir que los ports actuales son suficientes
- El cambio impactaría `AuthService`, `SecurityConfig`, `JwtAuthFilter` y `UserDetailsServiceImpl`

#### Trade-off aceptado
Pragmatismo y velocidad de entrega sobre pureza arquitectónica estricta. Decisión válida mientras Spring Security mantenga su robustez y soporte comunitario en el ecosistencia Java.