package edu.cit.franza.inventory;

/**
 * External shape for GET /api/inventory - deliberately separate from the
 * InventoryItem JPA entity so persistence details never leak into the
 * REST contract.
 */
public record InventorySummary(String productId, String name, int stock) {
}
