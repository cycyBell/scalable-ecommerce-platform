package com.rtxnano.ecommerce.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Optional request body for order cancellation requests.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelOrderRequestDto(
        String reason
) {
}
