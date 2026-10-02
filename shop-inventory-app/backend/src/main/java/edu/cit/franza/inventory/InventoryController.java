package edu.cit.franza.inventory;

import edu.cit.franza.inventory.model.InventoryItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Inventory module owns its own read endpoint rather than routing
 * inventory queries through the Order module - GET /api/inventory has
 * nothing to do with placing orders, so there's no reason for the shop
 * package to be involved.
 */
@RestController
class InventoryController {

    private final InventoryService inventoryService;

    InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/inventory")
    public List<InventorySummary> listInventory() {
        return inventoryService.listAll().stream()
                .map(this::toSummary)
                .toList();
    }

    private InventorySummary toSummary(InventoryItem item) {
        return new InventorySummary(item.getProductId(), item.getName(), item.getStock());
    }
}
