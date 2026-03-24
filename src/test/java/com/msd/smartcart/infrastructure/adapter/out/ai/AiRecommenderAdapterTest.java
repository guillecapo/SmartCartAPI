package com.msd.smartcart.infrastructure.adapter.out.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

class AiRecommenderAdapterTest {

    private AiRecommenderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AiRecommenderAdapter();
    }

    @Test
    void should_notThrow_when_suggestCalledWithValidArguments() {
        assertThatCode(() -> adapter.suggest("user-123", List.of("prod-001", "prod-002")))
                .doesNotThrowAnyException();
    }

    @Test
    void should_notThrow_when_suggestCalledWithEmptyProductList() {
        assertThatCode(() -> adapter.suggest("user-123", List.of()))
                .doesNotThrowAnyException();
    }
}