# Lab 4 Reflection

### Question 1
**Tiangge order TG-NPND74 (3 x PROD-003 and 2 x PROD-001) was accepted at 00:25:53. At that moment your last published stock for PROD-003 was 0, and the stock Tiangge worked out from your own decisions, cancellations and deliveries was 0. Where did your application's stock figure come from, and why did it disagree?**

My application's internal inventory figure came directly from a local database read where pending supplier order replenishment was credited prematurely or calculated against uncommitted in-flight stock. When purchase orders were submitted to LegacySupply, the application's ledger anticipated incoming units before the physical delivery status transitioned to DELIVERED. In contrast, Tiangge tracks available quantity strictly based on confirmed published stock updates and completed physical delivery receipts. Because our application checked an inventory state that reflected speculative replenishment rather than finalized on-hand stock, it accepted the order while Tiangge's ledger still recorded an available quantity of 0.

---

### Question 2
**Event evt_85742076a272d1f5 (order TG-4H6DYS) reached your application twice, as seq 224 and seq 237, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.**

The second delivery was rendered harmless by our deduplication check using `ProcessedEventRepository` backed by the PostgreSQL `processed_events` table:

```java
if (processedEventRepo.existsById(event.eventId())) {
    log.debug("Skipping already processed event: {}", event.eventId());
    return;
}
// ... after processing ...
processedEventRepo.save(new ProcessedEvent(event.eventId(), event.type(), event.orderId()));

### Question 3
**During your restart test your application was down for about 198 seconds while 5 orders arrived. How did the restarted application find those orders, and how did it avoid handling earlier ones again?**

The application tracks feed progress persistently using the channel_feed_cursor table: