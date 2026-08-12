package com.rtxnano.ecommerce.order.domain.statemachine;

import com.rtxnano.ecommerce.order.domain.enums.OrderStatus;
import com.rtxnano.ecommerce.order.domain.exception.InvalidOrderStateTransitionException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * ==============================================================================
 * ENGINE: Order State Machine
 * ==============================================================================
 * Enforces strict, deterministic state transitions for Order entities.
 * 
 * STATE TRANSITION GRAPH:
 * - PENDING   -> [PAID, CANCELLED]
 * - PAID      -> [SHIPPED, CANCELLED]
 * - SHIPPED   -> [DELIVERED]
 * - DELIVERED -> [] (Terminal State)
 * - CANCELLED -> [] (Terminal State)
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS;
    private static final Set<OrderStatus> TERMINAL_STATES;
    private static final Set<OrderStatus> CANCELLABLE_STATES;

    static {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);

        transitions.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        transitions.put(OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        transitions.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        transitions.put(OrderStatus.DELIVERED, Collections.emptySet());
        transitions.put(OrderStatus.CANCELLED, Collections.emptySet());

        VALID_TRANSITIONS = Collections.unmodifiableMap(transitions);
        TERMINAL_STATES = EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
        CANCELLABLE_STATES = EnumSet.of(OrderStatus.PENDING, OrderStatus.PAID);
    }

    private OrderStateMachine() {
        // Utility class, do not instantiate
    }

    /**
     * Checks if a transition from currentStatus to targetStatus is valid.
     */
    public static boolean canTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        if (currentStatus == targetStatus) {
            return false; // Idempotent/no-op transitions should not trigger state mutations
        }
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        return allowed.contains(targetStatus);
    }

    /**
     * Validates a transition and throws an InvalidOrderStateTransitionException if illegal.
     */
    public static void validateTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new InvalidOrderStateTransitionException(currentStatus, targetStatus);
        }
    }

    /**
     * Returns true if the order status is terminal (no further transitions possible).
     */
    public static boolean isTerminal(OrderStatus status) {
        return status != null && TERMINAL_STATES.contains(status);
    }

    /**
     * Returns true if the order can be cancelled at its current stage.
     */
    public static boolean isCancellable(OrderStatus status) {
        return status != null && CANCELLABLE_STATES.contains(status);
    }

    /**
     * Returns the set of valid next states from the given status.
     */
    public static Set<OrderStatus> getAllowedNextStates(OrderStatus currentStatus) {
        if (currentStatus == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(VALID_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet()));
    }
}
