package edu.cit.franza.shop.service;

import edu.cit.franza.events.OrderPlaced;
import edu.cit.franza.events.OrderRejected;
import edu.cit.franza.inventory.InventoryService;
import edu.cit.franza.inventory.ReservationResult;
import edu.cit.franza.inventory.model.InventoryItem;
import edu.cit.franza.shop.dto.CancelResponse;
import edu.cit.franza.shop.dto.InventorySnapshot;
import edu.cit.franza.shop.dto.ItemOutcome;
import edu.cit.franza.shop.dto.LineItem;
import edu.cit.franza.shop.dto.OrderHistoryEntry;
import edu.cit.franza.shop.dto.OrderResponse;
import edu.cit.franza.shop.exception.OrderNotFoundException;
import edu.cit.franza.shop.exception.OrderStateConflictException;
import edu.cit.franza.shop.model.OrderEntity;
import edu.cit.franza.shop.model.OrderItemEntity;
import edu.cit.franza.shop.model.OrderStatus;
import edu.cit.franza.shop.repository.OrderItemRepository;
import edu.cit.franza.shop.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * The Order module's entry point for placing, listing, and cancelling
 * orders.
 *
 * This is the in-process, module-to-module integration point of the whole
 * assignment: OrderService depends ONLY on the InventoryService interface
 * (constructor injection below), never on InventoryServiceImpl. It also
 * depends only on the neutral edu.cit.franza.events package to talk to
 * Notification - never on the notification package itself.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places a multi-item order. All-or-nothing: every line item is
     * validated against current stock before any reservation is made. If
     * any single item fails validation, the whole order is REJECTED and
     * nothing is reserved.
     *
     * The whole method runs in one transaction, and each reserve() call
     * acquires a row-level lock on that product (see
     * InventoryRepository.findWithLockByProductId) that is held until
     * this transaction commits or rolls back - so a second, concurrent
     * multi-item order touching the same product is blocked until this
     * one finishes, keeping the "all reservations happen together" rule
     * safe even under concurrency, not just when read sequentially.
     *
     * As a defensive second layer (in case the initial, unlocked
     * pre-validation read stale stock and an item fails during the
     * locked reserve() phase), any items already reserved earlier in the
     * same loop are explicitly restocked before the order is marked
     * REJECTED - a manual, in-process compensating action. See the
     * README reflection for how this differs from what a real saga would
     * need across a network.
     */
    @Transactional
    public OrderResponse placeOrder(List<LineItem> items) {
        String preRejectionReason = validateAll(items);

        if (preRejectionReason != null) {
            return persistRejected(items, preRejectionReason);
        }

        List<LineItem> reservedSoFar = new ArrayList<>();
        List<InventorySnapshot> snapshots = new ArrayList<>();

        for (LineItem item : items) {
            ReservationResult result = inventoryService.reserve(item.productId(), item.quantity());

            if (!result.success()) {
                // Race condition: something consumed stock between our
                // pre-validation read and this locked reserve() call.
                // Compensate everything this order already reserved.
                for (LineItem done : reservedSoFar) {
                    inventoryService.restock(done.productId(), done.quantity());
                }
                return persistRejected(items, "Concurrent update: " + result.reason());
            }

            reservedSoFar.add(item);
            if (result.inventoryItem() != null) {
                snapshots.add(toSnapshot(result.inventoryItem()));
            }
        }

        OrderEntity order = orderRepository.save(new OrderEntity(OrderStatus.CONFIRMED, null));
        List<ItemOutcome> outcomes = new ArrayList<>();
        for (LineItem item : items) {
            orderItemRepository.save(new OrderItemEntity(order.getOrderId(), item.productId(), item.quantity()));
            outcomes.add(new ItemOutcome(item.productId(), "RESERVED"));
        }

        eventPublisher.publishEvent(new OrderPlaced(order.getOrderId()));

        return new OrderResponse(order.getOrderId(), OrderStatus.CONFIRMED.name(), null, outcomes, snapshots);
    }

    /**
     * Cancels a CONFIRMED order and returns every reserved line item's
     * quantity to stock. Rejects (409) an order that's already
     * CANCELLED or was never CONFIRMED in the first place (nothing was
     * reserved for a REJECTED order, so there's nothing to restock).
     */
    @Transactional
    public CancelResponse cancelOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderStateConflictException("Order " + orderId + " is already cancelled");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new OrderStateConflictException(
                    "Order " + orderId + " cannot be cancelled from status " + order.getStatus());
        }

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItemEntity item : items) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        return new CancelResponse(orderId, OrderStatus.CANCELLED.name());
    }

    /**
     * Full order history with line items, newest first, for GET
     * /api/orders.
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryEntry> listOrders() {
        List<OrderEntity> orders = orderRepository.findAllByOrderByOrderIdDesc();
        List<Long> orderIds = orders.stream().map(OrderEntity::getOrderId).toList();
        List<OrderItemEntity> allItems = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orderIds);

        Map<Long, List<LineItem>> itemsByOrder = new LinkedHashMap<>();
        for (OrderItemEntity item : allItems) {
            itemsByOrder
                    .computeIfAbsent(item.getOrderId(), id -> new ArrayList<>())
                    .add(new LineItem(item.getProductId(), item.getQuantity()));
        }

        return orders.stream()
                .map(order -> new OrderHistoryEntry(
                        order.getOrderId(),
                        order.getStatus().name(),
                        order.getReason(),
                        order.getCreatedAt(),
                        itemsByOrder.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    /**
     * Read-only pass over every line item against current (unlocked)
     * stock. Returns the first validation failure found, or null if
     * every item is currently satisfiable.
     */
    private String validateAll(List<LineItem> items) {
        for (LineItem item : items) {
            Optional<InventoryItem> maybeInv = inventoryService.getItem(item.productId());

            if (maybeInv.isEmpty()) {
                return "Unknown product: " + item.productId();
            }

            InventoryItem inv = maybeInv.get();

            if (item.quantity() <= 0) {
                return "Quantity must be greater than zero for " + item.productId();
            }

            if (item.quantity() > inv.getStock()) {
                return "Insufficient stock for " + inv.getName()
                        + " (requested " + item.quantity() + ", available " + inv.getStock() + ")";
            }
        }
        return null;
    }

    private OrderResponse persistRejected(List<LineItem> items, String reason) {
        OrderEntity order = orderRepository.save(new OrderEntity(OrderStatus.REJECTED, reason));

        List<ItemOutcome> outcomes = new ArrayList<>();
        for (LineItem item : items) {
            orderItemRepository.save(new OrderItemEntity(order.getOrderId(), item.productId(), item.quantity()));
            outcomes.add(new ItemOutcome(item.productId(), "NOT RESERVED (order rejected)"));
        }

        eventPublisher.publishEvent(new OrderRejected(order.getOrderId(), reason));

        return new OrderResponse(order.getOrderId(), OrderStatus.REJECTED.name(), reason, outcomes, List.of());
    }

    private InventorySnapshot toSnapshot(InventoryItem item) {
        return new InventorySnapshot(item.getProductId(), item.getName(), item.getStock());
    }
}
