package edu.cit.franza.inventory;

import edu.cit.franza.inventory.model.InventoryItem;

/**
 * Outcome of an InventoryService.reserve() call. Public and part of the
 * module's contract - this is exactly the kind of thing the Order module
 * IS meant to see, as opposed to the repository/impl which it is not.
 */
public record ReservationResult(boolean success, String reason, InventoryItem inventoryItem) {

    public static ReservationResult approved(InventoryItem item) {
        return new ReservationResult(true, null, item);
    }

    public static ReservationResult rejected(String reason, InventoryItem item) {
        return new ReservationResult(false, reason, item);
    }
}
