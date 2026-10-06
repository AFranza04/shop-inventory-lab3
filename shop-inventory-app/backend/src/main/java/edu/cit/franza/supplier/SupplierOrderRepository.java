package edu.cit.franza.supplier;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierOrderRepository extends CrudRepository<SupplierOrder, Long> {
    List<SupplierOrder> findAll();
    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);
}