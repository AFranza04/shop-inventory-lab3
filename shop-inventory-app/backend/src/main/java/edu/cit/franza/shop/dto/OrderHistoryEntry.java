package edu.cit.franza.shop.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One entry in GET /api/orders - the full order header plus its line
 * items, for the order history / cancel view.
 */
public record OrderHistoryEntry(
        Long orderId,
        String status,
        String reason,
        OffsetDateTime createdAt,
        List<LineItem> items) {
}
