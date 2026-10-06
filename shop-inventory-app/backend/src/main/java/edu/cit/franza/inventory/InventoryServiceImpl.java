package edu.cit.franza.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.franza.events.LowStock;
import edu.cit.franza.events.StockChangedEvent;
import edu.cit.franza.inventory.model.InventoryItem;

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

        // Publish event for Tiangge channel synchronization
        eventPublisher.publishEvent(new StockChangedEvent(saved.getProductId(), saved.getStock()));

        if (saved.getStock() <= lowStockThreshold) {
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
        InventoryItem saved = inventoryRepository.save(item);

        // Publish event for Tiangge channel synchronization
        eventPublisher.publishEvent(new StockChangedEvent(saved.getProductId(), saved.getStock()));
    }
}