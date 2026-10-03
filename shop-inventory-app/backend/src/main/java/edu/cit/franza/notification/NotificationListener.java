package edu.cit.franza.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.franza.events.LowStock;
import edu.cit.franza.events.OrderPlaced;
import edu.cit.franza.events.OrderRejected;
import edu.cit.franza.supplier.SupplierGateway;

/**
 * Package-private: an implementation detail wired up purely through
 * Spring's event mechanism. Notification depends only on
 * edu.cit.franza.events - it never imports OrderService, InventoryService,
 * or anything from edu.cit.franza.shop / edu.cit.franza.inventory. Order
 * and Inventory, in turn, never import anything from this package - they
 * just publish events and have no idea Notification exists.
 *
 * Listeners run synchronously (no @Async) on purpose for this lab: it
 * keeps the write inside the same transaction/thread as the triggering
 * request, so GET /api/notifications immediately reflects an order that
 * was just placed - useful for demoing/capturing evidence right after an
 * action, and avoids the added complexity of a thread pool and
 * eventual-consistency ordering for what is, here, just a lightweight
 * database insert. See the README reflection for the trade-off.
 */
@Component
class NotificationListener {

    private final NotificationRepository notificationRepository;
    private final SupplierGateway supplierGateway;

    public NotificationListener(
            NotificationRepository notificationRepository, 
            SupplierGateway supplierGateway) {
        this.notificationRepository = notificationRepository;
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    void onOrderPlaced(OrderPlaced event) {
        notificationRepository.save(new NotificationEntity(
                NotificationType.ORDER_CONFIRMED,
                "Order " + event.orderId() + " confirmed"));
    }

    @EventListener
    void onOrderRejected(OrderRejected event) {
        notificationRepository.save(new NotificationEntity(
                NotificationType.ORDER_REJECTED,
                "Order " + event.orderId() + " rejected: " + event.reason()));
    }

    @EventListener
    public void handleLowStock(LowStock event) {

        int unitsToReorder = 50;
        supplierGateway.placeReorder(event.productId(), unitsToReorder);
    }
}
