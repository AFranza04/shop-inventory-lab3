package edu.cit.franza.events;

/**
 * Published by the Order module when an order fails validation (any line
 * item exceeds available stock, or an unknown product is requested) and
 * no reservations were made.
 */
public record OrderRejected(Long orderId, String reason) {
}
