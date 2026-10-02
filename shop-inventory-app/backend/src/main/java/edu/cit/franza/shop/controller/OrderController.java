package edu.cit.franza.shop.controller;

import edu.cit.franza.shop.dto.CancelResponse;
import edu.cit.franza.shop.dto.LineItem;
import edu.cit.franza.shop.dto.OrderHistoryEntry;
import edu.cit.franza.shop.dto.OrderRequest;
import edu.cit.franza.shop.dto.OrderResponse;
import edu.cit.franza.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * External client integration point: the React frontend talks to this
 * over plain HTTP/JSON. Everything downstream of here (Order -> Inventory,
 * Order -> Notification via events) is in-process.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        List<LineItem> items = request.getItems().stream()
                .map(li -> new LineItem(li.getProductId(), li.getQuantity()))
                .toList();
        return ResponseEntity.ok(orderService.placeOrder(items));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelResponse> cancelOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }

    @GetMapping
    public List<OrderHistoryEntry> listOrders() {
        return orderService.listOrders();
    }
}
