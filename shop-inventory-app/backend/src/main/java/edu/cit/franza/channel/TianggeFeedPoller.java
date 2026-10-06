package edu.cit.franza.channel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.cit.franza.inventory.InventoryService;
import edu.cit.franza.inventory.model.InventoryItem;
import edu.cit.franza.supplier.SupplierOrder;
import edu.cit.franza.supplier.SupplierOrderRepository;

@Component
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private final TianggeClient client;
    private final ChannelFeedCursorRepository cursorRepo;
    private final ProcessedEventRepository processedEventRepo;
    private final ChannelBackorderRepository backorderRepo;
    private final InventoryService inventoryService;
    private final SupplierOrderRepository supplierOrderRepo;

    TianggeFeedPoller(
        TianggeClient client,
        ChannelFeedCursorRepository cursorRepo,
        ProcessedEventRepository processedEventRepo,
        ChannelBackorderRepository backorderRepo,
        InventoryService inventoryService,
        SupplierOrderRepository supplierOrderRepo
    ) {
        this.client = client;
        this.cursorRepo = cursorRepo;
        this.processedEventRepo = processedEventRepo;
        this.backorderRepo = backorderRepo;
        this.inventoryService = inventoryService;
        this.supplierOrderRepo = supplierOrderRepo;
    }

    private String toLocalProductId(String channelSku) {
        return switch (channelSku) {
            case "PROD-001" -> "P100";
            case "PROD-002" -> "P200";
            case "PROD-003" -> "P300";
            default -> channelSku;
        };
    }

    private String toChannelSku(String localProductId) {
        return switch (localProductId) {
            case "P100" -> "PROD-001";
            case "P200" -> "PROD-002";
            case "P300" -> "PROD-003";
            default -> localProductId;
        };
    }

    private void syncSkuStock(String localProductId) {
        try {
            int currentQty = inventoryService.getItem(localProductId).map(InventoryItem::getStock).orElse(0);
            String channelSku = toChannelSku(localProductId);
            client.publishStock(List.of(new TianggeClient.StockItem(channelSku, currentQty)));
            log.info("Immediate stock sync sent to Tiangge for {}: {} units", channelSku, currentQty);
        } catch (Exception e) {
            log.warn("Failed to sync stock for {}: {}", localProductId, e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 2500)
    public void pollFeed() {
        try {
            ChannelFeedCursor cursor = cursorRepo.findById(1).orElseGet(() -> {
                ChannelFeedCursor c = new ChannelFeedCursor();
                c.setId(1);
                c.setLastCursor(0L);
                return cursorRepo.save(c);
            });

            long after = cursor.getLastCursor();
            TianggeClient.FeedResponse resp = client.getFeed(after, 20);

            if (resp == null || resp.events() == null || resp.events().isEmpty()) {
                return;
            }

            for (TianggeClient.FeedEvent event : resp.events()) {
                processEvent(event);
            }

            if (resp.nextCursor() != null && resp.nextCursor() > cursor.getLastCursor()) {
                cursor.setLastCursor(resp.nextCursor());
                cursor.setUpdatedAt(Instant.now());
                cursorRepo.save(cursor);
            }
        } catch (Exception e) {
            log.warn("Tiangge feed polling error: {}", e.getMessage());
        }
    }

    private void processEvent(TianggeClient.FeedEvent event) {
        if (processedEventRepo.existsById(event.eventId())) {
            log.debug("Skipping already processed event: {}", event.eventId());
            return;
        }

        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            try {
                if ("ORDER_PLACED".equalsIgnoreCase(event.type())) {
                    handleOrderPlaced(event);
                } else if ("ORDER_CANCELLED".equalsIgnoreCase(event.type())) {
                    handleOrderCancelled(event);
                }

                processedEventRepo.save(new ProcessedEvent(event.eventId(), event.type(), event.orderId()));
                return;
            } catch (org.springframework.web.client.HttpStatusCodeException ex) {
                if (ex.getStatusCode().is5xxServerError()) {
                    log.warn("Tiangge returned {} on event {}. Retrying {}/3...", ex.getStatusCode(), event.eventId(), attempts);
                    try {
                        Thread.sleep(1000L * attempts);
                    } catch (InterruptedException ignored) {}
                } else {
                    log.error("Failed processing event {}: {}", event.eventId(), ex.getMessage());
                    break;
                }
            } catch (Exception e) {
                log.error("Failed processing event {}: {}", event.eventId(), e.getMessage());
                break;
            }
        }
    }

    private void handleOrderPlaced(TianggeClient.FeedEvent event) {
        if (event.lines() == null || event.lines().isEmpty()) {
            client.sendDecision(event.orderId(), "REJECTED", "REJ-" + UUID.randomUUID().toString().substring(0, 8), "Empty lines");
            return;
        }

        // 1. Verify availability for ALL lines first
        boolean allAvailable = true;
        for (TianggeClient.FeedLine line : event.lines()) {
            String localSku = toLocalProductId(line.sellerSku());
            int available = inventoryService.getItem(localSku).map(InventoryItem::getStock).orElse(0);
            if (available < line.qty()) {
                allAvailable = false;
                break;
            }
        }

        if (allAvailable) {
            // Reserve all lines
            for (TianggeClient.FeedLine line : event.lines()) {
                inventoryService.reserve(toLocalProductId(line.sellerSku()), line.qty());
            }

            // Acknowledge to Tiangge first
            String internalOrderId = "SO-TG-" + event.orderId();
            client.sendDecision(event.orderId(), "ACCEPTED", internalOrderId, "Order accepted");
            log.info("Tiangge order {} ACCEPTED", event.orderId());

            // Immediately sync updated stock for each line
            for (TianggeClient.FeedLine line : event.lines()) {
                syncSkuStock(toLocalProductId(line.sellerSku()));
            }
            return;
        }

        // 2. Check if all unreserved lines are covered by open supplier orders
        boolean canBackorder = false;
        String backorderedLocalSku = null;
        int backorderedQty = 0;

        try {
            List<SupplierOrder> openOrders = supplierOrderRepo.findAll().stream()
                .filter(po -> po.getStatus() != null &&
                    po.getPoNumber() != null &&
                    !po.getPoNumber().isBlank() &&
                    ("SUBMITTED".equalsIgnoreCase(po.getStatus().name()) ||
                     "PROCESSING".equalsIgnoreCase(po.getStatus().name()) ||
                     "SHIPPED".equalsIgnoreCase(po.getStatus().name())))
                .toList();

            boolean allLinesEligible = true;

            for (TianggeClient.FeedLine line : event.lines()) {
                String localSku = toLocalProductId(line.sellerSku());
                boolean hasPo = openOrders.stream()
                    .anyMatch(po -> localSku.equals(po.getProductId()));

                if (hasPo) {
                    backorderedLocalSku = localSku;
                    backorderedQty = line.qty();
                } else {
                    allLinesEligible = false;
                    break;
                }
            }

            canBackorder = allLinesEligible && backorderedLocalSku != null;
        } catch (Exception ex) {
            log.warn("Error querying open supplier orders: {}", ex.getMessage());
            canBackorder = false;
        }

        if (canBackorder) {
            backorderRepo.save(new ChannelBackorder(event.orderId(), backorderedLocalSku, backorderedQty));
            client.sendDecision(event.orderId(), "BACKORDERED", "BO-" + event.orderId(), "Stock on order with supplier");
            log.info("Tiangge order {} BACKORDERED for {}", event.orderId(), backorderedLocalSku);
        } else {
            client.sendDecision(event.orderId(), "REJECTED", "REJ-" + event.orderId(), "Out of stock");
            log.info("Tiangge order {} REJECTED", event.orderId());
        }
    }

    private void handleOrderCancelled(TianggeClient.FeedEvent event) {
        if (event.lines() != null && !event.lines().isEmpty()) {
            for (TianggeClient.FeedLine line : event.lines()) {
                String localSku = toLocalProductId(line.sellerSku());
                try {
                    inventoryService.restock(localSku, line.qty());
                    syncSkuStock(localSku);
                } catch (Exception e) {
                    log.warn("Failed to restock cancelled item {}: {}", localSku, e.getMessage());
                }
            }
        }

        try {
            client.confirmCancellation(event.orderId());
            log.info("Tiangge cancellation confirmed for {}", event.orderId());
        } catch (Exception ex) {
            log.warn("Failed sending cancellation confirmation for {}: {}", event.orderId(), ex.getMessage());
        }

        for (String sku : List.of("P100", "P200", "P300")) {
            syncSkuStock(sku);
        }
    }
}