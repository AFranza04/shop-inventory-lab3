package edu.cit.franza.supplier;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import edu.cit.franza.inventory.InventoryService;

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
        try {
            client.fetchCatalog();
        } catch (Exception e) {
            log.warn("Could not fetch catalog on startup: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void retryPendingOrders() {
        List<SupplierOrder> pending = repo.findByStatusIn(List.of(SupplierOrderStatus.PENDING));
        for (SupplierOrder order : pending) {
            try {
                log.info("Retrying blocked/pending order ID {} (BuyerRef: {})", order.getId(), order.getBuyerRef());
                PurchaseOrderXml req = new PurchaseOrderXml("ZCV-3857", order.getCases(), order.getBuyerRef());
                PurchaseOrderAckXml ack = client.submitOrder(req, order.getRequestId());
                
                if (ack != null && ack.poNumber != null) {
                    order.setPoNumber(ack.poNumber);
                    order.setStatus(SupplierOrderStatus.SUBMITTED);
                    repo.save(order);
                    log.info("Pending order successfully placed with PO Number: {}", ack.poNumber);
                }
            } catch (Exception e) {
                log.warn("Retry failed for order {}: {}. Will retry next cycle.", order.getId(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 20000)
    public void pollOpenOrders() {
        List<SupplierOrder> active = repo.findByStatusIn(List.of(
            SupplierOrderStatus.SUBMITTED, 
            SupplierOrderStatus.PROCESSING, 
            SupplierOrderStatus.SHIPPED
        ));

        for (SupplierOrder order : active) {
            if (order.getPoNumber() == null) continue;
            try {
                // Throttle status checks to stay within LegacySupply rate limits
                Thread.sleep(1500);

                PurchaseOrderStatusXml statusXml = client.checkStatus(order.getPoNumber());
                if (statusXml == null || statusXml.statusCode == null) continue;

                String code = statusXml.statusCode.trim();

                switch (code) {
                    case "10" -> order.setStatus(SupplierOrderStatus.SUBMITTED);
                    case "20" -> order.setStatus(SupplierOrderStatus.PROCESSING);
                    case "30" -> order.setStatus(SupplierOrderStatus.SHIPPED);
                    case "40" -> {
                        order.setStatus(SupplierOrderStatus.DELIVERED);
                        repo.save(order);
                        log.info("Order {} DELIVERED! Restocking {} units of product {}", 
                                order.getPoNumber(), order.getUnits(), order.getProductId());
                        inventoryService.restock(order.getProductId(), order.getUnits());
                        continue;
                    }
                    case "90", "CANCELLED" -> {
                        order.setStatus(SupplierOrderStatus.CANCELLED);
                        repo.save(order);
                        log.warn("Order {} was CANCELLED by LegacySupply", order.getPoNumber());
                        continue;
                    }
                    default -> log.warn("Unrecognized status code for PO {}: '{}'", order.getPoNumber(), code);
                }

                repo.save(order);
            } catch (HttpClientErrorException.NotFound e) {
                // Catches 404 (E-PO-04: Order not found) when a cancelled order is purged or rejected
                log.warn("Order {} returned 404 (E-PO-04). Marking as CANCELLED.", order.getPoNumber());
                order.setStatus(SupplierOrderStatus.CANCELLED);
                repo.save(order);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Error polling PO {}: {}", order.getPoNumber(), e.getMessage());
            }
        }
    }
}