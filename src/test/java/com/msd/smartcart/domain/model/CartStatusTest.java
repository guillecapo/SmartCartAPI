package com.msd.smartcart.domain.model;

import com.msd.smartcart.domain.model.enums.CartStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartStatusTest {

    @Test
    void should_allowAddItems_when_statusIsActive() {
        Assertions.assertThat(CartStatus.ACTIVE.canAddItems()).isTrue();
    }

    @Test
    void should_notAllowAddItems_when_statusIsOrdered() {
        assertThat(CartStatus.ORDERED.canAddItems()).isFalse();
    }

    @Test
    void should_notAllowAddItems_when_statusIsCancelled() {
        assertThat(CartStatus.CANCELLED.canAddItems()).isFalse();
    }

    @Test
    void should_notBeClosed_when_statusIsActive() {
        assertThat(CartStatus.ACTIVE.isClosed()).isFalse();
    }

    @Test
    void should_beClosed_when_statusIsOrdered() {
        assertThat(CartStatus.ORDERED.isClosed()).isTrue();
    }

    @Test
    void should_beClosed_when_statusIsCancelled() {
        assertThat(CartStatus.CANCELLED.isClosed()).isTrue();
    }
}