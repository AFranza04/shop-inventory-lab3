package edu.cit.franza.inventory;

import edu.cit.franza.inventory.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

/**
 * Package-private: persistence access to the inventory table is an
 * implementation detail of this module. It lives in the same package as
 * InventoryServiceImpl (rather than a repository sub-package) specifically
 * so that "no modifier" visibility is actually enforced by the compiler -
 * code in edu.cit.franza.shop cannot see this type or call it, full stop.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    // Pessimistic row lock so two concurrent reserve() calls on the same
    // product can't both read stale stock and both succeed (overselling).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> findWithLockByProductId(String productId);

    List<InventoryItem> findAllByOrderByProductIdAsc();
}
