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
- Cart product limit — max 50 distinct products to protect database at checkout
- Batch stock validation — single MongoDB query at checkout regardless of cart size
- Global exception handler — architectural safety net and observability canary

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
| Testing | JUnit 5 + Mockito + AssertJ + Testcontainers |

---

## 🎯 Production-Ready Patterns

- **Spec-Driven Development** — OpenAPI contract defined and served via Swagger UI
- **Idempotency** — prevents duplicate cart operations and orders on client retries
- **Best-effort pattern** — MongoDB backup and AI recommendations degrade gracefully without aborting the main flow
- **Dead Letter Queue** — failed order notifications persist for reprocessing with exponential backoff (1s → 2s → 4s)
- **Auto-correction at checkout** — out-of-stock items removed automatically, user notified with full detail
- **Fail-fast on critical path** — Redis failures abort immediately, MongoDB backup failures are non-critical
- **Result type** — railway-oriented error handling, no unchecked exceptions leaking across layers
- **Cart product limit** — max 50 distinct products per cart, enforced at addItem
- **Batch stock validation** — single MongoDB query at checkout, never N queries
- **Global exception handler** — `@RestControllerAdvice` as architectural safety net

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

**Why limit cart to 50 distinct products?**
A single checkout triggers stock validation for every product in the cart. Without a limit, a cart with thousands of distinct products would generate a single massive MongoDB query, saturating the database. 50 is a deliberate trade-off — enough for any real shopping session, safe for the database under concurrent load.

**Why batch stock validation instead of per-product queries?**
N+1 queries at checkout is a classic production performance problem. `findAllByIds` resolves all products in one round-trip regardless of cart size, eliminating query amplification under load.

**Why GlobalExceptionHandler as architectural canary?**
`@RestControllerAdvice` is the last line of defense — it catches any exception that escapes the adapter and service layers without being translated. Beyond returning a structured `AppError` response to the client without leaking internal details, it serves as an observability signal: if hits appear in production logs, an adapter is not translating its exceptions correctly. Zero hits means the architecture is clean.

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
Pragmatismo y velocidad de entrega sobre pureza arquitectónica estricta. Decisión válida mientras Spring Security mantenga su robustez y soporte comunitario en el ecosistema Java.

### ADR-002: Cart product limit — max 50 distinct products

**Fecha:** 2026
**Estado:** Aceptado

#### Contexto
Sin límite en el número de productos distintos por carrito, un checkout con miles de productos generaría una consulta masiva a MongoDB, saturando la base de datos bajo carga concurrente.

#### Decisión
Máximo 50 productos distintos por carrito. La validación vive en `CartService.addItem()` — solo aplica al agregar un producto nuevo, no al incrementar cantidad de uno existente.

#### Trade-off aceptado
Suficiente para cualquier sesión de compra real. Protege la base de datos bajo carga concurrente.

### ADR-003: Batch stock validation at checkout

**Fecha:** 2026
**Estado:** Aceptado

#### Contexto
La validación de stock original hacía una consulta a MongoDB por cada producto del carrito — patrón N+1 que amplifica la carga bajo concurrencia.

#### Decisión
`validateStock` usa `findAllByIds` para resolver todos los productos en una sola consulta, luego cruza en memoria con `Collectors.toMap`.

#### Trade-off aceptado
Ligero incremento en memoria (todos los productos en memoria simultáneamente) a cambio de eliminar la amplificación de queries. Con límite de 50 productos, el impacto en memoria es despreciable.

### ADR-004: GlobalExceptionHandler como canary arquitectónico

**Fecha:** 2026
**Estado:** Aceptado

#### Contexto
Excepciones no traducidas en adapters podían escapar al controller, retornando 500 genérico sin estructura ni contexto.

#### Decisión
`GlobalExceptionHandler` con `@RestControllerAdvice` como última línea de defensa. Retorna `AppError` estructurado sin exponer detalles internos. Sirve además como señal de observabilidad — hits frecuentes indican un adapter con traducción de excepciones incompleta.

#### Trade-off aceptado
La malla de circo no reemplaza el manejo correcto en cada capa — es el indicador de que algo falló, no la solución permanente.