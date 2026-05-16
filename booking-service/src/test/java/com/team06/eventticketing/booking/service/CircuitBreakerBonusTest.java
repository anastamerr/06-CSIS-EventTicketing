package com.team06.eventticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CircuitBreakerBonusTest {

    @Test
    void circuitOpensFallsBackAndRecovers() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("bonus-feign-demo", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .permittedNumberOfCallsInHalfOpenState(1)
                .waitDurationInOpenState(Duration.ofMillis(10))
                .build());
        AtomicBoolean downstreamRecovered = new AtomicBoolean(false);
        Supplier<String> downstreamCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            if (!downstreamRecovered.get()) {
                throw new IllegalStateException("downstream unavailable");
            }
            return "recovered";
        });

        assertThrows(IllegalStateException.class, downstreamCall::get);
        assertThrows(IllegalStateException.class, downstreamCall::get);
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertEquals("fallback", callWithFallback(downstreamCall));

        downstreamRecovered.set(true);
        circuitBreaker.transitionToHalfOpenState();

        assertEquals("recovered", downstreamCall.get());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    private String callWithFallback(Supplier<String> downstreamCall) {
        try {
            return downstreamCall.get();
        } catch (CallNotPermittedException exception) {
            return "fallback";
        }
    }
}
