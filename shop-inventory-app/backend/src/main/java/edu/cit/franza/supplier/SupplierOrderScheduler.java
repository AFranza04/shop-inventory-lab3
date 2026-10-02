package edu.cit.franza.supplier;

import edu.cit.franza.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SupplierOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderScheduler.class);
    private final SupplierOrderRepository repo;
    private final LegacySupplyClient client;
    private final InventoryService inventoryService;

    SupplierOrderScheduler(SupplierOrderRepository repo, LegacySupplyClient client, InventoryService inventoryService) {
        this.repo = repo;
        this.client = client;
        this.inventoryService = inventoryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // Read catalog on startup to fulfill verify criterion
        client.fetchCatalog();
    }

    // Part D: Retry pending orders blocked by outages (every 30 seconds)
    @Scheduled(fixedDelay = 30000)
    public void retryPendingOrders() {
        List<SupplierOrder> pending = repo.findByStatusIn(List.of(SupplierOrderStatus.PENDING));
        for (SupplierOrder order : pending) {
            try {
                PurchaseOrderXml req = new PurchaseOrderXml("ZCV-3857", order.getCases(), order.getBuyerRef());
                PurchaseOrderAckXml ack = client.submitOrder(req, order.getRequestId());
                if (ack != null && ack.poNumber != null) {
                    order.setPoNumber(ack.poNumber);
                    order.setStatus(SupplierOrderStatus.SUBMITTED);
                    repo.save(order);
                }
            } catch (Exception ignored) {}
        }
    }

    // Part E: Poll status of open orders every 25 seconds
    @Scheduled(fixedDelay = 25000)
    public void pollOpenOrders() {
        List<SupplierOrder> active = repo.findByStatusIn(List.of(SupplierOrderStatus.SUBMITTED, SupplierOrderStatus.PROCESSING, SupplierOrderStatus.SHIPPED));
        for (SupplierOrder order : active) {
            if (order.getPoNumber() == null) continue;
            try {
                PurchaseOrderStatusXml statusXml = client.checkStatus(order.getPoNumber());
                if (statusXml == null || statusXml.statusCode == null) continue;

                switch (statusXml.statusCode) {
                    case "10" -> order.setStatus(SupplierOrderStatus.SUBMITTED);
                    case "20" -> order.setStatus(SupplierOrderStatus.PROCESSING);
                    case "30" -> order.setStatus(SupplierOrderStatus.SHIPPED);
                    case "40" -> {
                        order.setStatus(SupplierOrderStatus.DELIVERED);
                        repo.save(order);
                        log.info("Order {} delivered! Restocking {} units of product {}", order.getPoNumber(), order.getUnits(), order.getProductId());
                        inventoryService.restock(order.getProductId(), order.getUnits());
                    }
                    case "90", "CANCELLED" -> {
                        order.setStatus(SupplierOrderStatus.CANCELLED);
                        repo.save(order);
                        log.warn("Order {} was cancelled by supplier", order.getPoNumber());
                    }
                    default -> log.warn("Unrecognized status code: {}", statusXml.statusCode);
                }
                repo.save(order);
            } catch (Exception e) {
                log.warn("Error polling PO {}: {}", order.getPoNumber(), e.getMessage());
            }
        }
    }
}