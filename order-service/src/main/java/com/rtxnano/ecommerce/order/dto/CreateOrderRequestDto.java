package com.rtxnano.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new order from current cart contents.
 */
public class CreateOrderRequestDto {

    @NotBlank(message = "Shipping address is required")
    @Size(min = 5, max = 500, message = "Shipping address must be between 5 and 500 characters")
    private String shippingAddress;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code (e.g. USD)")
    private String currency = "USD";

    public CreateOrderRequestDto() {
    }

    public CreateOrderRequestDto(String shippingAddress) {
        this.shippingAddress = shippingAddress;
        this.currency = "USD";
    }

    public CreateOrderRequestDto(String shippingAddress, String currency) {
        this.shippingAddress = shippingAddress;
        this.currency = (currency != null && !currency.isBlank()) ? currency.toUpperCase() : "USD";
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getCurrency() {
        return (currency != null && !currency.isBlank()) ? currency.toUpperCase() : "USD";
    }

    public void setCurrency(String currency) {
        this.currency = (currency != null && !currency.isBlank()) ? currency.toUpperCase() : "USD";
    }

    @Override
    public String toString() {
        return "CreateOrderRequestDto{" +
                "shippingAddress='" + shippingAddress + '\'' +
                ", currency='" + currency + '\'' +
                '}';
    }
}
