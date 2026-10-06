package edu.cit.franza.events;

public record StockChangedEvent(String productId, int newQuantity) {}