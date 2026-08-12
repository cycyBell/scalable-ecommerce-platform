package com.rtxnano.ecommerce.order.repository;

import com.rtxnano.ecommerce.order.domain.entity.Order;
import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for Order entities.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Retrieves paginated orders for a specific user.
     */
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    /**
     * Finds an order by its ID and user ID for security/ownership enforcement.
     */
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds orders by lifecycle status for administration/fulfillment.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Eagerly fetches an order along with its line items in a single query.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    /**
     * Eagerly fetches an order with items, enforcing user ownership.
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id AND o.userId = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") UUID id, @Param("userId") UUID userId);
}
