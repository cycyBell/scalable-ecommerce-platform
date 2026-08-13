package com.rtxnano.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Line item DTO returned by Shopping Cart Service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItemResponseDto {

    private String productId;
    private String title;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;

    @JsonProperty("stockQuantity")
    private Integer stockQuantity;

    private String imageUrl;

    @JsonProperty("isAvailable")
    private Boolean isAvailable;

    public CartItemResponseDto() {
    }

    public CartItemResponseDto(String productId, String title, BigDecimal price, Integer quantity, BigDecimal subtotal) {
        this.productId = productId;
        this.title = title;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = subtotal;
        this.isAvailable = true;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsAvailable() {
        return isAvailable != null ? isAvailable : true;
    }

    public void setIsAvailable(Boolean available) {
        isAvailable = available;
    }

    @Override
    public String toString() {
        return "CartItemResponseDto{" +
                "productId='" + productId + '\'' +
                ", title='" + title + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", subtotal=" + subtotal +
                ", isAvailable=" + isAvailable +
                '}';
    }
}
