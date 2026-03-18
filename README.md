# SmartCart API
API de carrito de compras construida con arquitectura hexagonal, diseñada para escalar y mantener separación estricta entre dominio, aplicación e infraestructura.

## Stack

- Java 21 — LTS, records, sealed interfaces, pattern matching
- Spring Boot 3.3
- Redis — carritos activos (TTL 24h)
- MongoDB — backup de carritos y órdenes
- RabbitMQ — notificaciones event-driven
- ULID — IDs ordenables cronológicamente

## Arquitectura
```
domain/          → Modelos, errores, ports (sin dependencias externas)
application/     → Orquestación de casos de uso (sin imports de infraestructura)
infrastructure/  → Adapters de entrada y salida (HTTP, Redis, MongoDB, RabbitMQ)
shared/          → Result, AppError, excepciones de dominio
```
El dominio no conoce Spring, Redis, ni MongoDB. Si mañana cambias Redis por Memcached, solo tocas el adapter.

## Decisiones arquitectónicas

### ¿Por qué persistencia dual Redis + MongoDB?
Redis es la fuente de verdad para carritos activos — lecturas/escrituras en O(1), TTL nativo de 24h. MongoDB entra solo como backup cuando `totalValue > $500` OR `itemCount > 20`, porque perder un carrito de $10 es aceptable, perder uno de $800 no lo es.

**Trade-off aceptado:** Carritos que nunca superan los umbrales y expiran en Redis se pierden permanentemente. El usuario debe reconstruirlo. Decisión validada con negocio.

Orden de escritura:
1. Redis primero — si falla, aborta y retorna error
2. MongoDB segundo — best-effort, si falla loguea y continúa

### ¿Por qué validación lazy de stock?
El stock se valida únicamente en checkout, no al agregar productos al carrito. Esto reduce latencia en `addItem` y evita consultas innecesarias — el inventario puede cambiar entre que el usuario agrega un producto y confirma la compra de todas formas.

**Trade-off aceptado:** El usuario puede agregar productos sin stock al carrito. En checkout el sistema auto-corrige removiendo los productos no disponibles y notifica al usuario qué fue removido para que decida si procede.

### ¿Por qué auto-corrección en checkout?
Cuando hay productos sin stock, el sistema los remueve automáticamente del carrito y devuelve `CheckoutResult.OutOfStock` con la lista de productos removidos. El cliente reintenta manualmente — no procesamos automáticamente con los items restantes porque es decisión del usuario si acepta comprar menos de lo que eligió.

### ¿Por qué ULID para orderId?
ULID embebe un timestamp en los primeros 48 bits, lo que lo hace lexicográficamente ordenable. Ordenar órdenes por fecha no requiere campo `createdAt` separado ni índice adicional en MongoDB. Los índices son más compactos porque los IDs son secuenciales.

UUID se usa para `cartId` — los carritos no necesitan ordenamiento cronológico.

### ¿Por qué excepciones de dominio en lugar de excepciones de Spring?
```java
// ❌ Esto acopla la capa de aplicación a Spring Data
catch (DuplicateKeyException e) { ... }

// ✅ El adapter traduce, el servicio no sabe qué tecnología falló
catch (DuplicateOrderException e) { ... }
```
`InfrastructureException` y `DuplicateOrderException` viven en `shared/exception/` — son contratos de dominio, no de infraestructura. Si cambias MongoDB por PostgreSQL, `CheckoutService` no se entera.

### ¿Por qué idempotency key en addItem y checkout?
`addItem` es aditivo — si el cliente reintenta sin idempotency key, el producto se duplica en el carrito. La key garantiza que un retry devuelve la respuesta cacheada sin re-ejecutar la operación.

**Contrato documentado:** El cliente debe reutilizar la misma key en cada retry. Si genera una key nueva, el sistema no puede detectar el duplicado.

### ¿Por qué RabbitMQ y no Kafka para notificaciones?
Las notificaciones de orden confirmada son eventos de un solo consumidor, no requieren replay ni retención de historial. RabbitMQ con Dead Letter Queue y backoff exponencial (1s → 2s → 4s, 3 intentos) es suficiente y operacionalmente más simple.

**Kafka sería correcto si:** múltiples servicios necesitaran consumir el mismo evento, o si necesitáramos replay de eventos históricos.

---

## Flujo de checkout
```
1. Obtener carrito activo (Redis)
2. Validar carrito no vacío
3. Validar stock de TODOS los items — lazy, en paralelo
4. Si hay productos sin stock → auto-corregir carrito → OutOfStock
5. Persistir orden en MongoDB
6. Limpiar carrito en Redis
7. Publicar OrderConfirmedEvent a RabbitMQ (best-effort)
8. Retornar Confirmed
```

## Manejo de fallos

| Componente | Fallo | Comportamiento |
|---|---|---|
| Redis (lectura) | InfrastructureException | Aborta, retorna error |
| Redis (escritura) | InfrastructureException | Aborta, retorna error |
| MongoDB backup | Cualquier excepción | Log warn, continúa |
| MongoDB orden | InfrastructureException | Aborta, retorna error |
| RabbitMQ | Falla tras 3 reintentos | Persiste en DLQ, continúa |
| AI Recommendations | Cualquier excepción | Log warn, continúa |

---

## Deuda técnica documentada

**Carritos perdidos por TTL** — Carritos que nunca superan umbrales de backup y expiran en Redis no son recuperables. Si el negocio decide que esto es inaceptable, la solución es bajar los umbrales o persistir todos los carritos en MongoDB independientemente del valor.

**Duplicado por clearCart fallido** — Si `clearCart` falla tras confirmar una orden, el usuario verá su carrito intacto y podría reintentar el checkout con una nueva idempotency key, generando una orden duplicada. Mitigación pendiente: job de limpieza de carritos huérfanos.

**findByCartId en Redis** — `ActiveCartRepository` no soporta búsqueda por `cartId`, solo por `userId`. Si en el futuro se necesita, requiere un índice secundario en Redis (`cart:id:{cartId} → userId`).

**AuthService acoplado a Spring Security** — `AuthService` en la capa de aplicación utiliza directamente `AuthenticationManager` y `PasswordEncoder` de Spring Security. Se evaluó abstraer estas dependencias detrás de ports propios pero se descartó. Ver ADR-001.

---

## Architecture Decision Records

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