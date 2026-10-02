package edu.cit.franza.shop.dto;

import java.util.List;

/**
 * Response body for POST /api/orders:
 * { "status": "...", "reason": "...", "items": [...], "inventory": [...] }
 */
public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<ItemOutcome> items,
        List<InventorySnapshot> inventory) {
}
