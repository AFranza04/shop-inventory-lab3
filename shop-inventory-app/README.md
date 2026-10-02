# Shop + Inventory + Notification — Modular Monolith over Supabase

A single Spring Boot app with three modules — **Order** (`edu.cit.franza.shop`),
**Inventory** (`edu.cit.franza.inventory`), and **Notification** (`edu.cit.franza.notification`)
— integrated in-process, backed by a shared Supabase Postgres database, fronted by a React
(Vite) client over REST.

This is Lab 2, extending the Lab 1 modular monolith with: multi-item orders with
all-or-nothing rollback, order cancellation with restock, read endpoints for live
inventory/order data, and in-process domain events (Order/Inventory → Notification) driving
both an activity feed and a low-stock business rule.

## Project layout

```
backend/    Spring Boot app (Java 17, Maven)
  edu.cit.franza                 -> @SpringBootApplication (scans all three modules)
  edu.cit.franza.shop            -> Order module (controller, service, repo, dto, model, exception)
  edu.cit.franza.inventory       -> Inventory module (service, package-private impl, repo, model)
  edu.cit.franza.notification    -> Notification module (package-private entity/repo/listener, public controller + DTO)
  edu.cit.franza.events          -> Neutral domain event types (OrderPlaced, OrderRejected, LowStock)
  edu.cit.franza.config          -> CORS config
frontend/   React + Vite client (cart, live inventory table, order history, notification feed)
sql/        schema.sql — creates + seeds the Supabase tables (orders, order_items, inventory, notifications)
```

## Architecture at a glance

- **Order → Inventory**: in-process, same JVM, via the `InventoryService` interface only
  (unchanged from Lab 1). `OrderService.placeOrder` validates every line item against current
  stock *before* reserving anything; only if every item passes does it call
  `InventoryService.reserve()` for each one, all inside one `@Transactional` method.
- **Order/Inventory → Notification**: also in-process, but decoupled through Spring's
  `ApplicationEventPublisher`/`@EventListener` rather than a direct method call. `OrderService`
  publishes `OrderPlaced`/`OrderRejected`; `InventoryServiceImpl` publishes `LowStock` directly
  whenever a `reserve()` leaves stock under the configured threshold. Neither Order nor
  Inventory imports anything from `edu.cit.franza.notification` — they only depend on the
  neutral `edu.cit.franza.events` package. Notification, in turn, only depends on that same
  events package — never on `OrderService` or `InventoryService`.
- **Order/Inventory/Notification → Supabase**: Spring Data JPA over JDBC, one connection pool.
- **React → Spring Boot**: plain REST/JSON over HTTP — the only network hop in the system.

## Enforced module boundaries

`InventoryServiceImpl` and `InventoryRepository` remain **package-private**, in the same
package (`edu.cit.franza.inventory`) so the "no modifier" visibility is actually
compiler-enforced — `edu.cit.franza.shop` cannot import them, full stop. The Notification
module follows the same pattern: `NotificationEntity`, `NotificationType`,
`NotificationRepository`, and `NotificationListener` are all package-private; only
`NotificationController` and `NotificationSummary` (its REST DTO) are public, because that's
the module's deliberate external contract. The event classes in `edu.cit.franza.events` are
public by design — they're the shared, neutral vocabulary all three modules are allowed to
depend on without depending on each other.

## Supabase setup

1. Go to [supabase.com](https://supabase.com), sign in, and create a project (or reuse your
   Lab 1 project). Note your database password.
2. Open **SQL Editor → New query**, paste in the contents of
   [`sql/schema.sql`](sql/schema.sql), and run it. This **drops and recreates everything**
   (`inventory`, `orders`, `order_items`, `notifications`) from scratch, including seed data —
   don't hand-edit the schema in the Supabase UI; always go through this script so it's the
   single source of truth.
3. Get your connection string from **Project Settings → Database → Connection string**. If
   your network can't resolve the direct `db.<ref>.supabase.co` host (common IPv6-only issue),
   use the **pooler** connection instead — see the troubleshooting note below.
4. Set these as real environment variables (never commit real values):

   ```
   SUPABASE_DB_URL=jdbc:postgresql://<host-from-supabase>:<port>/postgres
   SUPABASE_DB_USERNAME=postgres[.<project-ref> if using the pooler]
   SUPABASE_DB_PASSWORD=<your db password>
   CORS_ALLOWED_ORIGIN=http://localhost:5173
   LOW_STOCK_THRESHOLD=5
   ```

   `backend/.env.example` documents the same variables.

   > **Pooler note**: if the direct host doesn't resolve, use Supabase's connection pooler
   > (Project → Connect → Transaction pooler): host like
   > `aws-0-<region>.pooler.supabase.com`, port `6543`, and username
   > `postgres.<project-ref>` instead of plain `postgres`.

## Running it

**Backend**
```bash
cd backend
# load your .env into the shell, or set the vars another way
mvn spring-boot:run        # or ./mvnw spring-boot:run if you've generated the wrapper
```
Runs on `http://localhost:8080`.

**Frontend**
```bash
cd frontend
npm install
cp .env.example .env.local
npm run dev
```
Runs on `http://localhost:5173`.

## API

**`POST /api/orders`** — place a multi-item order
```json
// request
{ "items": [{ "productId": "P100", "quantity": 2 }, { "productId": "P200", "quantity": 1 }] }

// response — confirmed
{
  "orderId": 7, "status": "CONFIRMED", "reason": null,
  "items": [{ "productId": "P100", "outcome": "RESERVED" }, { "productId": "P200", "outcome": "RESERVED" }],
  "inventory": [{ "productId": "P100", "name": "Wireless Mouse", "stock": 23 }, { "productId": "P200", "name": "Mechanical Keyboard", "stock": 9 }]
}

// response — rejected (one item exceeds stock -> whole order rejected, nothing reserved)
{
  "orderId": 8, "status": "REJECTED",
  "reason": "Insufficient stock for USB-C Hub (requested 1, available 0)",
  "items": [
    { "productId": "P100", "outcome": "NOT RESERVED (order rejected)" },
    { "productId": "P300", "outcome": "NOT RESERVED (order rejected)" }
  ],
  "inventory": []
}
```

**`POST /api/orders/{orderId}/cancel`** — cancel a confirmed order and restock its items
```json
{ "orderId": 7, "status": "CANCELLED" }
```
`404` if the order doesn't exist; `409` if it's already cancelled or was never confirmed.

**`GET /api/inventory`** — live stock levels
```json
[{ "productId": "P100", "name": "Wireless Mouse", "stock": 23 }, ...]
```

**`GET /api/orders`** — order history with line items
```json
[{ "orderId": 7, "status": "CONFIRMED", "reason": null, "createdAt": "...", "items": [{ "productId": "P100", "quantity": 2 }] }]
```

**`GET /api/notifications`** — activity feed
```json
[{ "notificationId": 12, "type": "LOW_STOCK", "message": "Reorder needed: USB-C Hub (P300) is down to 0 in stock", "createdAt": "..." }]
```

## A note on synchronous event listeners

`NotificationListener`'s `@EventListener` methods are **not** `@Async`. They run on the same
thread, inside the same transaction, as the request that triggered them. For this lab that's
the right call: notifications are lightweight database inserts, and keeping them synchronous
means `GET /api/notifications` immediately reflects an order that was just placed — important
for capturing the Network tab evidence below without a race between the write and the next
read. The trade-off is that a slow or failing notification write could, in theory, add latency
to (or roll back alongside) the order request itself; `@Async` would decouple that at the cost
of eventual consistency and needing to reason about thread pools and listener failures
separately from the request lifecycle.

## Network tab evidence

**1. Multi-item order, all items succeed (CONFIRMED)**

`![Confirmed Order](screenshots/screenshot-1-confirmed.png)`

**2. Multi-item order, one item fails, whole order REJECTED with no partial reservation**

`![Confirmed Order](screenshots/screenshot-1-confirmed.png)`

**3. Cancel with restock reflected in GET /api/inventory**

`![Cancel Restock](screenshots/screenshot-3-cancel-restock.png)`

**4. Notification feed showing a confirmed order, a rejected order, and a low-stock alert**

`![Notification Feed](screenshots/screenshot-4-notifications.png)`

---

## Reflection

**1. Atomicity of multi-item orders, in-process vs. split.** In-process, `placeOrder` wraps
everything in one `@Transactional` method, and each `reserve()` call takes a pessimistic
row-level lock on that product that's held until the whole transaction commits or rolls back —
so a concurrent order touching the same product blocks until this one finishes, not just until
one `reserve()` call returns. On top of that, `OrderService` pre-validates every line item
before reserving any of them, and if a later `reserve()` unexpectedly fails anyway (a race
between the unlocked pre-check and the locked reserve), it explicitly restocks everything the
order already reserved before marking it REJECTED — a manual, in-process compensating action.
If Order and Inventory were split across a network, none of this holds automatically: there's
no shared database transaction spanning both services, so a real saga is needed — either an
orchestrator that calls reserve() for each item and issues compensating restock() calls on
failure (what I already do manually, but now over the network with retries/timeouts), or a
choreographed approach using an outbox and idempotent event handlers so a crashed step doesn't
leave inventory half-reserved forever.

**2. Events vs. direct calls.** Publishing `OrderPlaced`/`OrderRejected`/`LowStock` instead of
calling Notification directly means Order and Inventory have zero compile-time or runtime
dependency on Notification — they don't know it exists, and Notification could be deleted
entirely without touching either module. That's a much looser coupling than even the
interface-based Order→Inventory link, since Order doesn't hold a reference to anything
Notification-shaped at all; it just publishes a fact. If Notification became its own
microservice, in-process pub/sub wouldn't be enough — I'd need a message broker (Kafka,
RabbitMQ, SQS) so Order/Inventory publish to a topic instead of calling
`ApplicationEventPublisher`, plus delivery guarantees (at-least-once with idempotent
consumers, since "Order 7 confirmed" being logged twice is harmless but losing it silently
isn't), and likely an outbox pattern so publishing the event and committing the order write
happen atomically instead of risking a crash between the two.

**3. What to extract first.** I'd extract **Notification** first, not Inventory. It's already
the most decoupled module (event-driven, no other module calls into it), it has no strict
consistency requirement with Order/Inventory (a slightly-delayed notification is fine, a
slightly-stale stock count isn't), and it's the lowest-risk cut: Order and Inventory keep
working identically if Notification disappears, since neither depends on it. Code-wise, I'd
swap `ApplicationEventPublisher.publishEvent(...)` calls in Order/Inventory for a message
broker client publishing the same event payloads to a topic, stand up Notification as its own
Spring Boot app subscribing to that topic with its own `notifications` table/database, and
build `GET /api/notifications` as a small read API there instead of a controller in this
monolith - the event *shapes* (`OrderPlaced`, `OrderRejected`, `LowStock`) stay essentially
unchanged, which is exactly why designing them as a neutral, serializable-looking contract
now pays off later.
