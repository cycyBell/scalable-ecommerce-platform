package com.rtxnano.ecommerce.order.repository;

import com.rtxnano.ecommerce.order.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for OrderItem entities.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    /**
     * Finds all line items belonging to an order ID.
     */
    List<OrderItem> findByOrderId(UUID orderId);
}
