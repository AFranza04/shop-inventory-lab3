package edu.cit.franza.shop.repository;

import edu.cit.franza.shop.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByOrderByOrderIdDesc();
}
