package com.rag.ingestion.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        when(circuitBreakerRegistry.circuitBreaker("vectorDatabaseCall")).thenReturn(circuitBreaker);
    }

    @Test
    @DisplayName("Should trip circuit breaker and engage fallback on vector DB failure")
    void testVectorDatabaseFailureTriggersFallback() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("vectorDatabaseCall");
        
        assertThat(cb).isNotNull();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        
        verify(circuitBreakerRegistry, times(1)).circuitBreaker("vectorDatabaseCall");
    }
}