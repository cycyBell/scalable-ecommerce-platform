package com.rtxnano.ecommerce.order.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for stock adjustments in Product Catalog Service.
 */
public class StockAdjustmentRequestDto {

    @JsonProperty("quantity_change")
    private int quantityChange;

    public StockAdjustmentRequestDto() {
    }

    public StockAdjustmentRequestDto(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }
}
