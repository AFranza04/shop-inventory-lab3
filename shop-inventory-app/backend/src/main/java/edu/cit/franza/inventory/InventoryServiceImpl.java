package edu.cit.franza.inventory;

import edu.cit.franza.events.LowStock;
import edu.cit.franza.inventory.model.InventoryItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Package-private on purpose. This class - and the repository it uses -
 * are implementation details of the inventory module. Because it has no
 * access modifier, code outside edu.cit.franza.inventory cannot import it,
 * declare a variable of this type, or autowire it directly, even though
 * it's a Spring bean. Other modules (namely edu.cit.franza.shop) can only
 * ever obtain a reference to it through the public InventoryService
 * interface, via constructor injection - Spring is happy to inject a
 * package-private bean into anything that asks for its public interface
 * type, because the wiring happens through reflection, not compiled
 * source references. See README section 2 for what breaks if this class
 * is made public instead.
 *
 * Publishes LowStock events directly (via ApplicationEventPublisher)
 * rather than calling the Notification module - this keeps Inventory
 * decoupled from Notification exactly the same way Order is: Inventory
 * only ever imports the neutral edu.cit.franza.events package, never
 * edu.cit.franza.notification.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItem> getItem(String productId) {
        return inventoryRepository.findById(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> listAll() {
        return inventoryRepository.findAllByOrderByProductIdAsc();
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        Optional<InventoryItem> maybeItem = inventoryRepository.findWithLockByProductId(productId);

        if (maybeItem.isEmpty()) {
            return ReservationResult.rejected("Unknown product: " + productId, null);
        }

        InventoryItem item = maybeItem.get();

        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be greater than zero", item);
        }

        if (quantity > item.getStock()) {
            return ReservationResult.rejected(
                    "Insufficient stock for " + item.getName()
                            + " (requested " + quantity + ", available " + item.getStock() + ")",
                    item);
        }

        item.setStock(item.getStock() - quantity);
        InventoryItem saved = inventoryRepository.save(item);

        if (saved.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStock(saved.getProductId(), saved.getName(), saved.getStock()));
        }

        return ReservationResult.approved(saved);
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findWithLockByProductId(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot restock unknown product: " + productId));
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }
}
