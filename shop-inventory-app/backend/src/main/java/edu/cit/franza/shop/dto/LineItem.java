package edu.cit.franza.shop.dto;

/**
 * A single requested line item, decoupled from the JSON request shape so
 * OrderService's method signature doesn't depend on the web-layer DTO.
 */
public record LineItem(String productId, int quantity) {
}
