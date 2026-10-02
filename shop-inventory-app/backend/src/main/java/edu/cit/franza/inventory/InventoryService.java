package edu.cit.franza.inventory;

import edu.cit.franza.inventory.model.InventoryItem;

import java.util.List;
import java.util.Optional;

/**
 * The entire public surface of the inventory module. The Order module
 * (edu.cit.franza.shop) is only ever allowed to depend on this interface,
 * never on InventoryServiceImpl or InventoryRepository directly - those
 * are package-private and simply cannot be referenced from outside
 * edu.cit.franza.inventory.
 */
public interface InventoryService {

    /**
     * Look up a single inventory item by product id.
     */
    Optional<InventoryItem> getItem(String productId);

    /**
     * All inventory items, in product id order. Backs GET /api/inventory.
     */
    List<InventoryItem> listAll();

    /**
     * Attempt to reserve (deduct) the given quantity from stock.
     * Rejects the reservation - without throwing - if the requested
     * quantity exceeds current stock, or if the product doesn't exist.
     * If the resulting stock falls below the configured low-stock
     * threshold, publishes a LowStock event.
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Return a previously reserved quantity to stock (used when an order
     * is cancelled). No-op-safe: if the product doesn't exist this is a
     * logic error by the caller and throws, since restock should only
     * ever be called for quantities this service itself reserved.
     */
    void restock(String productId, int quantity);
}
