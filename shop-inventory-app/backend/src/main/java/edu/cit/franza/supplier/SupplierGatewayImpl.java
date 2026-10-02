package edu.cit.franza.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);
    private final SupplierOrderRepository orderRepository;
    private final LegacySupplyClient client;

    record SkuMapping(String sku, int packSize) {}

    // Map your internal product IDs to LegacySupply SKUs and pack sizes
    private final Map<String, SkuMapping> catalogMapping = Map.of(
        "PROD-001", new SkuMapping("ZCV-3857", 12),
        "PROD-002", new SkuMapping("ZCV-6567", 10),
        "PROD-003", new SkuMapping("ZCV-2235", 20)
    );

    SupplierGatewayImpl(SupplierOrderRepository orderRepository, LegacySupplyClient client) {
        this.orderRepository = orderRepository;
        this.client = client;
    }

    @Override
    @Transactional
    public SupplierOrderResult placeReorder(String productId, int unitsNeeded) {
        SkuMapping mapping = catalogMapping.getOrDefault(productId, new SkuMapping("ZCV-3857", 12));

        // Ceiling division: cases = ceil(units / packSize)
        int cases = Math.max(1, (unitsNeeded + mapping.packSize() - 1) / mapping.packSize());
        int totalExpectedUnits = cases * mapping.packSize();

        // Save as PENDING first so no reorder is ever lost
        SupplierOrder order = new SupplierOrder(productId, cases, totalExpectedUnits, UUID.randomUUID().toString());
        order = orderRepository.save(order);

        String buyerRef = "RO-" + order.getId();
        order.setBuyerRef(buyerRef);
        orderRepository.save(order);

        try {
            PurchaseOrderXml req = new PurchaseOrderXml(mapping.sku(), cases, buyerRef);
            PurchaseOrderAckXml ack = client.submitOrder(req, order.getRequestId());
            if (ack != null && ack.poNumber != null) {
                order.setPoNumber(ack.poNumber);
                order.setStatus(SupplierOrderStatus.SUBMITTED);
                orderRepository.save(order);
            }
        } catch (Exception e) {
            log.warn("LegacySupply call failed. Order {} kept as PENDING for retry: {}", order.getId(), e.getMessage());
        }

        return new SupplierOrderResult(
            order.getId(),
            order.getBuyerRef(),
            order.getPoNumber(),
            order.getCases(),
            order.getUnits(),
            order.getStatus()
        );
    }
}