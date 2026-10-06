package edu.cit.franza.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.cit.franza.config.InstanceContext;
import edu.cit.franza.inventory.InventoryService;
import edu.cit.franza.inventory.model.InventoryItem;

@Component
class ChannelStartupCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ChannelStartupCoordinator.class);

    private final TianggeClient client;
    private final InstanceContext instanceContext;
    private final InventoryService inventoryService;

    ChannelStartupCoordinator(TianggeClient client, InstanceContext instanceContext, InventoryService inventoryService) {
        this.client = client;
        this.instanceContext = instanceContext;
        this.inventoryService = inventoryService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Starting shop instance ID: {}", instanceContext.getInstanceId());
        try {
            client.sendHeartbeat();
            log.info("Initial Tiangge heartbeat sent successfully.");

            // Tiangge expects PROD-001, PROD-002, PROD-003 mapped to supplier catalog items
            List<TianggeClient.ListingItem> listings = List.of(
                new TianggeClient.ListingItem("PROD-001", "Wireless Mouse", "ZCV-3857"),
                new TianggeClient.ListingItem("PROD-002", "Mechanical Keyboard", "ZCV-6567"),
                new TianggeClient.ListingItem("PROD-003", "USB-C Hub", "ZCV-2235")
            );
            client.publishListings(listings);
            log.info("Published {} product listings to Tiangge.", listings.size());

            syncInitialStock();
        } catch (Exception e) {
            log.error("Failed to complete startup sequence with Tiangge: {}", e.getMessage(), e);
        }
    }

    private void syncInitialStock() {
        try {
            List<TianggeClient.StockItem> stocks = List.of(
                new TianggeClient.StockItem("PROD-001", getStock("P100")),
                new TianggeClient.StockItem("PROD-002", getStock("P200")),
                new TianggeClient.StockItem("PROD-003", getStock("P300"))
            );
            client.publishStock(stocks);
            log.info("Initial stock published to Tiangge: {}", stocks);
        } catch (Exception e) {
            log.warn("Failed to publish initial stock: {}", e.getMessage());
        }
    }

    private int getStock(String productId) {
        return inventoryService.getItem(productId)
            .map(InventoryItem::getStock)
            .orElse(0);
    }

    @Scheduled(fixedRate = 30000)
    public void scheduledHeartbeat() {
        try {
            client.sendHeartbeat();
            log.debug("Tiangge heartbeat sent.");
        } catch (Exception e) {
            log.warn("Failed to send scheduled heartbeat to Tiangge: {}", e.getMessage());
        }
    }
}