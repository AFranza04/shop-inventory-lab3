package edu.cit.franza.events;

/**
 * Published by the Order module once an order is fully confirmed and all
 * line items have been reserved. Lives in a neutral top-level package
 * (not inside edu.cit.franza.shop) specifically so the Notification
 * module can depend on the event's shape without depending on Shop or
 * Inventory internals - it only ever imports from edu.cit.franza.events.
 */
public record OrderPlaced(Long orderId) {
}
