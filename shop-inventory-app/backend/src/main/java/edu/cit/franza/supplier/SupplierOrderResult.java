package edu.cit.franza.supplier;

public record SupplierOrderResult(
    Long internalOrderId,
    String buyerRef,
    String poNumber,
    int casesOrdered,
    int expectedUnits,
    SupplierOrderStatus status
) {}