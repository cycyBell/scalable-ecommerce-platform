package com.rtxnano.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level shopping cart payload returned by Shopping Cart Service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartResponseDto {

    private String cartKey;
    private List<CartItemResponseDto> items = new ArrayList<>();
    private Integer totalItems;
    private BigDecimal totalAmount;

    public CartResponseDto() {
    }

    public CartResponseDto(String cartKey, List<CartItemResponseDto> items, Integer totalItems, BigDecimal totalAmount) {
        this.cartKey = cartKey;
        this.items = items != null ? items : new ArrayList<>();
        this.totalItems = totalItems;
        this.totalAmount = totalAmount;
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    public String getCartKey() {
        return cartKey;
    }

    public void setCartKey(String cartKey) {
        this.cartKey = cartKey;
    }

    public List<CartItemResponseDto> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponseDto> items) {
        this.items = items;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "CartResponseDto{" +
                "cartKey='" + cartKey + '\'' +
                ", itemsCount=" + (items != null ? items.size() : 0) +
                ", totalItems=" + totalItems +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
