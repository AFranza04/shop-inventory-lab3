package edu.cit.franza.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the "inventory" table in Supabase Postgres.
 * Public: the Order module is allowed to read this shape (it's returned
 * in the API response as the post-order inventory snapshot), it just
 * isn't allowed to touch inventory persistence directly.
 */
@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "stock", nullable = false)
    private int stock;

    protected InventoryItem() {
        // required by JPA
    }

    public InventoryItem(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
