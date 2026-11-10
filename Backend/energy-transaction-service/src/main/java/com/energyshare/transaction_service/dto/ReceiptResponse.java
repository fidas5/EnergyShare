package com.energyshare.transaction_service.dto;

import com.energyshare.transaction_service.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

/** Justificatif d'echange d'energie (disponible uniquement une fois la transaction cloturee). */
public record ReceiptResponse(String receiptNumber, Long transactionId, Long reservationId,
                              Long buyerId, Long buyerHouseholdId, Long sellerId, Long sellerHouseholdId,
                              BigDecimal energyKwh, BigDecimal pricePerKwh, BigDecimal totalAmount,
                              String paymentReference, PaymentMethod paymentMethod,
                              Instant paidAt, Instant completedAt, Instant generatedAt) {
}
