package edu.cit.franza.supplier;

public interface SupplierGateway {
    SupplierOrderResult placeReorder(String productId, int unitsNeeded);
}