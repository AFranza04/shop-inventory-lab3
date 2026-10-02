package edu.cit.franza.events;

/**
 * Published by the Inventory module immediately after a successful
 * reserve() whenever the resulting stock for a product falls below the
 * configured low-stock threshold. Independent of OrderPlaced/OrderRejected
 * because it's triggered by an inventory-level business rule, not by the
 * order's own outcome - a single multi-item order can trigger zero, one,
 * or several LowStock events (one per affected product).
 */
public record LowStock(String productId, String productName, int currentStock) {
}
