package edu.cit.franza.shop.dto;

/**
 * One entry in OrderResponse.items() - what happened to this specific
 * line item. "outcome" is a short human-readable string, e.g.
 * "RESERVED", "REJECTED: Insufficient stock for USB-C Hub (...)", or
 * "NOT RESERVED (order rejected)".
 */
public record ItemOutcome(String productId, String outcome) {
}
