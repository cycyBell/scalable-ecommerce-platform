package com.rtxnano.ecommerce.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * DTO / EVENT: PaymentResultEventDto
 * ==============================================================================
 * AMQP JSON payload received on 'order.payment-result.queue' indicating the outcome
 * of payment processing from the Payment Microservice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResultEventDto(
        UUID orderId,
        UUID userId,
        String paymentId,
        String status,
        BigDecimal amount,
        String failureReason,
        Instant timestamp
) {

    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }
}
