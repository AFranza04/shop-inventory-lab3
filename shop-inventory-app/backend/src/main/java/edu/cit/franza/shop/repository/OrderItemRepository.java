package edu.cit.franza.shop.repository;

import edu.cit.franza.shop.model.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {

    List<OrderItemEntity> findByOrderId(Long orderId);

    List<OrderItemEntity> findByOrderIdIn(List<Long> orderIds);
}
