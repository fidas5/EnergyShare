package com.energyshare.transaction_service.messaging;

import com.energyshare.transaction_service.domain.Transaction;
import com.energyshare.transaction_service.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Evenement publie sur RabbitMQ (TransactionConfirmed / TransactionCancelled). */
public record TransactionEvent(String eventType, Long transactionId, Long reservationId,
                               Long buyerId, Long sellerId, BigDecimal energyKwh, BigDecimal totalAmount,
                               TransactionStatus status, Instant occurredAt) {

    public static final String CONFIRMED = "TransactionConfirmed";
    public static final String CANCELLED = "TransactionCancelled";

    public static TransactionEvent of(String type, Transaction t) {
        return new TransactionEvent(type, t.getId(), t.getReservationId(), t.getBuyerId(), t.getSellerId(),
                t.getEnergyKwh(), t.getTotalAmount(), t.getStatus(), Instant.now());
    }
}
