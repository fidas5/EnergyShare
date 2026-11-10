package com.energyshare.transaction_service.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.energyshare.transaction_service.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionStatusTest {

    @Test
    void transitionsAllowed() {
        assertThat(TransactionStatus.PENDING_PAYMENT.canTransitionTo(TransactionStatus.PAID)).isTrue();
        assertThat(TransactionStatus.PAID.canTransitionTo(TransactionStatus.COMPLETED)).isTrue();
        assertThat(TransactionStatus.PAID.canTransitionTo(TransactionStatus.CANCELLED)).isTrue();
    }

    @Test
    void transitionsForbidden() {
        assertThat(TransactionStatus.PENDING_PAYMENT.canTransitionTo(TransactionStatus.COMPLETED)).isFalse();
        assertThat(TransactionStatus.COMPLETED.canTransitionTo(TransactionStatus.CANCELLED)).isFalse();
        assertThat(TransactionStatus.CANCELLED.canTransitionTo(TransactionStatus.PAID)).isFalse();
    }

    @Test
    void changeStatusSetsTimestampAndRejectsInvalid() {
        Transaction tx = Transaction.create(1L, 10L, 100L, 20L, 200L,
                new BigDecimal("10"), new BigDecimal("0.25"), new BigDecimal("2.50"));
        tx.changeStatus(TransactionStatus.PAID);
        assertThat(tx.getPaidAt()).isNotNull();
        assertThatThrownBy(() -> tx.changeStatus(TransactionStatus.PENDING_PAYMENT))
                .isInstanceOf(ApiException.class);
    }
}
