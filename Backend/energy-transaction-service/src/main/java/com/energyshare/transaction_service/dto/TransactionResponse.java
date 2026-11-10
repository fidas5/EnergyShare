package com.energyshare.transaction_service.dto;

import com.energyshare.transaction_service.domain.Transaction;
import com.energyshare.transaction_service.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id, Long reservationId,
                                  Long buyerId, Long buyerHouseholdId, Long sellerId, Long sellerHouseholdId,
                                  BigDecimal energyKwh, BigDecimal pricePerKwh, BigDecimal totalAmount,
                                  TransactionStatus status,
                                  Instant createdAt, Instant updatedAt,
                                  Instant paidAt, Instant completedAt, Instant cancelledAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getReservationId(),
                t.getBuyerId(), t.getBuyerHouseholdId(), t.getSellerId(), t.getSellerHouseholdId(),
                t.getEnergyKwh(), t.getPricePerKwh(), t.getTotalAmount(), t.getStatus(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getPaidAt(), t.getCompletedAt(), t.getCancelledAt());
    }
}
