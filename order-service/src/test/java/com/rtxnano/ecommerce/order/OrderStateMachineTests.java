package com.rtxnano.ecommerce.order;

import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.exception.InvalidOrderStateTransitionException;
import com.rtxnano.ecommerce.order.domain.statemachine.OrderStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Order State Machine Unit Tests")
class OrderStateMachineTests {

    @Nested
    @DisplayName("Valid Transition Tests")
    class ValidTransitions {

        @ParameterizedTest(name = "{0} -> {1} should be valid")
        @CsvSource({
                "PENDING, PAID",
                "PENDING, CANCELLED",
                "PAID, SHIPPED",
                "PAID, CANCELLED",
                "SHIPPED, DELIVERED"
        })
        void shouldAllowValidTransitions(OrderStatus from, OrderStatus to) {
            assertTrue(OrderStateMachine.canTransition(from, to),
                    () -> String.format("Expected transition from %s to %s to be allowed", from, to));
            // validateTransition should not throw
            OrderStateMachine.validateTransition(from, to);
        }
    }

    @Nested
    @DisplayName("Invalid Transition Tests")
    class InvalidTransitions {

        @ParameterizedTest(name = "{0} -> {1} should be rejected")
        @CsvSource({
                "PENDING, SHIPPED",
                "PENDING, DELIVERED",
                "PAID, PENDING",
                "PAID, DELIVERED",
                "SHIPPED, PENDING",
                "SHIPPED, PAID",
                "SHIPPED, CANCELLED",
                "DELIVERED, PENDING",
                "DELIVERED, PAID",
                "DELIVERED, SHIPPED",
                "DELIVERED, CANCELLED",
                "CANCELLED, PENDING",
                "CANCELLED, PAID",
                "CANCELLED, SHIPPED",
                "CANCELLED, DELIVERED"
        })
        void shouldRejectInvalidTransitions(OrderStatus from, OrderStatus to) {
            assertFalse(OrderStateMachine.canTransition(from, to),
                    () -> String.format("Expected transition from %s to %s to be rejected", from, to));

            InvalidOrderStateTransitionException ex = assertThrows(
                    InvalidOrderStateTransitionException.class,
                    () -> OrderStateMachine.validateTransition(from, to)
            );
            assertEquals(from, ex.getCurrentStatus());
            assertEquals(to, ex.getTargetStatus());
        }

        @Test
        @DisplayName("Same state transition should be rejected (non-mutating)")
        void shouldRejectSameStateTransition() {
            for (OrderStatus status : OrderStatus.values()) {
                assertFalse(OrderStateMachine.canTransition(status, status),
                        () -> String.format("Transition to same state %s should return false", status));
            }
        }

        @Test
        @DisplayName("Null state transition should be rejected safely")
        void shouldRejectNullStates() {
            assertFalse(OrderStateMachine.canTransition(null, OrderStatus.PAID));
            assertFalse(OrderStateMachine.canTransition(OrderStatus.PENDING, null));
            assertFalse(OrderStateMachine.canTransition(null, null));
        }
    }

    @Nested
    @DisplayName("Terminal & Cancellable State Tests")
    class StatePropertyTests {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
        void shouldIdentifyTerminalStates(OrderStatus status) {
            assertTrue(OrderStateMachine.isTerminal(status), () -> status + " should be terminal");
            assertEquals(Set.of(), OrderStateMachine.getAllowedNextStates(status));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "PAID", "SHIPPED"})
        void shouldIdentifyNonTerminalStates(OrderStatus status) {
            assertFalse(OrderStateMachine.isTerminal(status), () -> status + " should not be terminal");
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "PAID"})
        void shouldIdentifyCancellableStates(OrderStatus status) {
            assertTrue(OrderStateMachine.isCancellable(status), () -> status + " should be cancellable");
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELLED"})
        void shouldIdentifyNonCancellableStates(OrderStatus status) {
            assertFalse(OrderStateMachine.isCancellable(status), () -> status + " should not be cancellable");
        }
    }
}
