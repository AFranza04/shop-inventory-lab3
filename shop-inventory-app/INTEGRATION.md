# INTEGRATION.md - LegacySupply Integration Notes

## 1. Product Mapping Table

| Internal Product ID | Internal Name | LegacySupply SKU | PackSize | UOM |
|:---|:---|:---|:---|:---|
| PROD-001 | Wireless Mouse | ZCV-3857 | 12 | CS |
| PROD-002 | Mechanical Keyboard | ZCV-6567 | 10 | CS |
| PROD-003 | USB-C Cable | ZCV-2235 | 20 | CS |

*(Note: Replace PROD-001, PROD-002, PROD-003 with the exact product_id values from your Supabase inventory table)*

---

## 2. Session Lifecycle and Expiration
- **Endpoint:** `POST /api/v1/auth/token`
- **Lifespan:** Active sessions expire after ~15 minutes or when invalidated.
- **Handling:** In-memory caching with proactive re-authentication. If any request receives HTTP `401 Unauthorized` or `E-AUTH-*` errors, the cached token is purged, a new token is requested, and the original request is immediately retried.

---

## 3. Error Codes Encountered & Mitigation

| Code | HTTP | Cause | Handling |
|:---|:---|:---|:---|
| `E-AUTH-01` | 401 | Credentials rejected | Validate client ID and API key configuration |
| `E-AUTH-02` / `03` / `07` | 401 | Session missing, unrecogized, or expired | Invalidate cached token, generate new session token, retry call |
| `E-FMT-01` / `02` | 415 / 400 | Malformed XML payload | Ensure UTF-8 XML serialization with matching schema |
| `E-REF-05` | 400 | Invalid BuyerRef format | Restrict BuyerRef to valid alphanumeric identifier |
| `E-SKU-02` | 422 | SKU not in partner catalog | Validate product mapping against `/catalog` |
| `E-QTY-11` | 422 | Quantity not between 1 and 99 | Ensure order quantity satisfies $1 \le \text{Qty} \le 99$ |
| `E-IDEM-04` | 409 | `X-Request-Id` reused with differing body | Guarantee consistent `X-Request-Id` generated at order creation |
| `E-RATE-03` | 429 | Request quota exceeded | Exponential backoff; keep polling interval $\ge 20\text{s}$ |
| `E-SYS-50` / `99` | 503 | Server processing error / Downstream outage | Keep status as `PENDING`; retry via scheduled background worker |

---

## 4. Qty and UOM (Unit of Measure)
- **Concept:** LegacySupply trades wholesale items in Cases (`CS`), whereas our internal store tracks individual units (`EA`).
- **Formula:** Always round up using ceiling division to guarantee inventory demand is satisfied:
  $$\text{Cases} = \left\lceil \frac{\text{Units Needed}}{\text{Pack Size}} \right\rceil = \frac{\text{Units Needed} + \text{Pack Size} - 1}{\text{Pack Size}}$$
- **Worked Example:**
  - Need: 15 Wireless Mice (`PROD-001`).
  - Pack Size: 12 units per case (`ZCV-3857`).
  - Calculation: $\lceil 15 / 12 \rceil = 2\text{ cases}$.
  - Expected delivery: $2 \times 12 = 24\text{ units}$.

---

## 5. Handling Unexpected & Cancelled Statuses
- If LegacySupply returns a cancelled or unrecognized status code during polling, the local order status transitions to `CANCELLED` or `FAILED`.
- The system logs an alert and halts further automatic restocking for that order to prevent erroneous inventory inflation.