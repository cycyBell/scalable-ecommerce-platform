package com.rtxnano.ecommerce.order.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * ==============================================================================
 * DOMAIN ENTITY: OrderItem
 * ==============================================================================
 * Represents an individual line item associated with an Order, snapshotting price and quantity.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "product_id", nullable = false, length = 64, updatable = false)
    private String productId;

    @Column(name = "product_name", nullable = false, length = 255, updatable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotal;

    public OrderItem() {
    }

    public OrderItem(String productId, String productName, BigDecimal unitPrice, Integer quantity) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = calculateSubtotal(unitPrice, quantity);
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.subtotal == null && this.unitPrice != null && this.quantity != null) {
            this.subtotal = calculateSubtotal(this.unitPrice, this.quantity);
        }
    }

    public static BigDecimal calculateSubtotal(BigDecimal price, Integer qty) {
        if (price == null || qty == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(qty));
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        this.subtotal = calculateSubtotal(this.unitPrice, this.quantity);
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        this.subtotal = calculateSubtotal(this.unitPrice, this.quantity);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return Objects.equals(id, orderItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "id=" + id +
                ", productId='" + productId + '\'' +
                ", productName='" + productName + '\'' +
                ", unitPrice=" + unitPrice +
                ", quantity=" + quantity +
                ", subtotal=" + subtotal +
                '}';
    }
}
