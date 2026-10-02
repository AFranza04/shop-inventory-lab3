package edu.cit.franza.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for POST /api/orders:
 * { "items": [{ "productId": "...", "quantity": n }, ...] }
 */
public class OrderRequest {

    @NotEmpty(message = "items must contain at least one line item")
    @Valid
    private List<LineItemRequest> items;

    public OrderRequest() {
    }

    public OrderRequest(List<LineItemRequest> items) {
        this.items = items;
    }

    public List<LineItemRequest> getItems() {
        return items;
    }

    public void setItems(List<LineItemRequest> items) {
        this.items = items;
    }

    public static class LineItemRequest {

        @NotBlank(message = "productId is required")
        private String productId;

        @Min(value = 1, message = "quantity must be at least 1")
        private int quantity;

        public LineItemRequest() {
        }

        public LineItemRequest(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
