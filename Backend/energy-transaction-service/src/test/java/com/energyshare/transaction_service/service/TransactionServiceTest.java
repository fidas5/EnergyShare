package com.energyshare.transaction_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.energyshare.transaction_service.client.MarketplaceClient;
import com.energyshare.transaction_service.client.ReservationDto;
import com.energyshare.transaction_service.domain.Payment;
import com.energyshare.transaction_service.domain.PaymentMethod;
import com.energyshare.transaction_service.domain.PaymentStatus;
import com.energyshare.transaction_service.domain.Transaction;
import com.energyshare.transaction_service.domain.TransactionStatus;
import com.energyshare.transaction_service.dto.PaymentRequest;
import com.energyshare.transaction_service.exception.ApiException;
import com.energyshare.transaction_service.messaging.TransactionEvent;
import com.energyshare.transaction_service.payment.PaymentResult;
import com.energyshare.transaction_service.payment.PaymentSimulator;
import com.energyshare.transaction_service.repository.PaymentRepository;
import com.energyshare.transaction_service.repository.TransactionHistoryRepository;
import com.energyshare.transaction_service.repository.TransactionRepository;
import com.energyshare.transaction_service.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactions;
    @Mock PaymentRepository payments;
    @Mock TransactionHistoryRepository histories;
    @Mock MarketplaceClient marketplace;
    @Mock PaymentSimulator simulator;
    @Mock ApplicationEventPublisher events;

    private TransactionService service() {
        return new TransactionService(transactions, payments, histories, marketplace, simulator, events);
    }

    private final AuthenticatedUser buyer = new AuthenticatedUser(10L, 100L, false);
    private final AuthenticatedUser seller = new AuthenticatedUser(20L, 200L, false);
    private final AuthenticatedUser stranger = new AuthenticatedUser(99L, 900L, false);

    private Transaction tx(TransactionStatus target) {
        Transaction t = Transaction.create(1L, 10L, 100L, 20L, 200L,
                new BigDecimal("10"), new BigDecimal("0.25"), new BigDecimal("2.50"));
        ReflectionTestUtils.setField(t, "id", 5L);
        if (target == TransactionStatus.PAID || target == TransactionStatus.COMPLETED) t.changeStatus(TransactionStatus.PAID);
        if (target == TransactionStatus.COMPLETED) t.changeStatus(TransactionStatus.COMPLETED);
        return t;
    }

    private ReservationDto reservation(String status) {
        return new ReservationDto(1L, 10L, 100L, 20L, 200L, new BigDecimal("10"), new BigDecimal("0.25"), status);
    }

    @Test
    void create_computesTotalAndStartsPendingPayment() {
        when(marketplace.getReservation(1L)).thenReturn(reservation("RESERVED"));
        when(transactions.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction created = service().create(1L, buyer);

        assertThat(created.getStatus()).isEqualTo(TransactionStatus.PENDING_PAYMENT);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void create_forbiddenWhenCallerIsNotTheBuyer() {
        when(marketplace.getReservation(1L)).thenReturn(reservation("RESERVED"));

        assertThatThrownBy(() -> service().create(1L, stranger))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void create_conflictWhenReservationAlreadyHasTransaction() {
        when(marketplace.getReservation(1L)).thenReturn(reservation("RESERVED"));
        when(transactions.existsByReservationIdAndStatusNot(1L, TransactionStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> service().create(1L, buyer))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void pay_declinedStoresFailedPaymentAndReturns402() {
        Transaction t = tx(TransactionStatus.PENDING_PAYMENT);
        when(transactions.findById(5L)).thenReturn(Optional.of(t));
        when(simulator.processPayment(any(), any(), any())).thenReturn(PaymentResult.declined("refusé"));

        assertThatThrownBy(() -> service().pay(5L, new PaymentRequest(PaymentMethod.CARD, "tok_declined"), buyer))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.PENDING_PAYMENT);
    }

    @Test
    void pay_successMarksPaidAndPublishesConfirmedEvent() {
        Transaction t = tx(TransactionStatus.PENDING_PAYMENT);
        when(transactions.findById(5L)).thenReturn(Optional.of(t));
        when(simulator.processPayment(any(), any(), any())).thenReturn(PaymentResult.success("PAY-1"));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        var response = service().pay(5L, new PaymentRequest(PaymentMethod.WALLET, null), buyer);

        assertThat(response.transactionStatus()).isEqualTo(TransactionStatus.PAID);
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(TransactionEvent.CONFIRMED);
    }

    @Test
    void pay_conflictWhenAlreadyPaid() {
        when(transactions.findById(5L)).thenReturn(Optional.of(tx(TransactionStatus.PAID)));

        assertThatThrownBy(() -> service().pay(5L, new PaymentRequest(PaymentMethod.WALLET, null), buyer))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void complete_onlySellerAndOnlyWhenPaid() {
        when(transactions.findById(5L)).thenReturn(Optional.of(tx(TransactionStatus.PAID)));
        assertThatThrownBy(() -> service().complete(5L, buyer))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        Transaction pending = tx(TransactionStatus.PENDING_PAYMENT);
        when(transactions.findById(6L)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service().complete(6L, seller))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancel_paidRefundsPaymentAndPublishesCancelledEvent() {
        Transaction t = tx(TransactionStatus.PAID);
        Payment p = Payment.of(5L, new BigDecimal("2.50"), PaymentMethod.WALLET, PaymentStatus.SUCCESS, "PAY-1", null);
        when(transactions.findById(5L)).thenReturn(Optional.of(t));
        when(payments.findFirstByTransactionIdAndStatusOrderByCreatedAtDesc(5L, PaymentStatus.SUCCESS)).thenReturn(Optional.of(p));

        service().cancel(5L, "Changement de plan", seller);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(TransactionEvent.CANCELLED);
    }

    @Test
    void cancel_conflictWhenAlreadyCompleted() {
        when(transactions.findById(5L)).thenReturn(Optional.of(tx(TransactionStatus.COMPLETED)));

        assertThatThrownBy(() -> service().cancel(5L, null, buyer))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void get_forbiddenForStrangerAllowedForAdmin() {
        Transaction t = tx(TransactionStatus.PENDING_PAYMENT);
        when(transactions.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service().get(5L, stranger))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service().get(5L, new AuthenticatedUser(1L, null, true))).isSameAs(t);
    }
}
