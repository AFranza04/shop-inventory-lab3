package edu.cit.franza.shop.dto;

/**
 * The shop module's own trimmed view of an inventory item, embedded in
 * the order response so the client can see updated stock without a
 * second request. Intentionally separate from
 * edu.cit.franza.inventory.InventorySummary - each module owns its own
 * external contract even though the shapes happen to match today.
 */
public record InventorySnapshot(String productId, String name, int stock) {
}
