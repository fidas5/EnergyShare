package com.energyshare.transaction_service.domain;

import com.energyshare.transaction_service.exception.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_reservation", columnList = "reservationId"),
        @Index(name = "idx_tx_buyer_household", columnList = "buyerHouseholdId"),
        @Index(name = "idx_tx_seller_household", columnList = "sellerHouseholdId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long buyerId;

    private Long buyerHouseholdId;

    @Column(nullable = false)
    private Long sellerId;

    private Long sellerHouseholdId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal energyKwh;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal pricePerKwh;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant paidAt;
    private Instant completedAt;
    private Instant cancelledAt;

    /** Verrou optimiste : evite deux paiements / annulations concurrents. */
    @Version
    private Long version;

    public static Transaction create(Long reservationId, Long buyerId, Long buyerHouseholdId,
                                     Long sellerId, Long sellerHouseholdId,
                                     BigDecimal energyKwh, BigDecimal pricePerKwh, BigDecimal totalAmount) {
        Transaction t = new Transaction();
        t.reservationId = reservationId;
        t.buyerId = buyerId;
        t.buyerHouseholdId = buyerHouseholdId;
        t.sellerId = sellerId;
        t.sellerHouseholdId = sellerHouseholdId;
        t.energyKwh = energyKwh;
        t.pricePerKwh = pricePerKwh;
        t.totalAmount = totalAmount;
        t.status = TransactionStatus.PENDING_PAYMENT;
        t.createdAt = Instant.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    /** Applique une transition de statut ; 409 si elle n'est pas autorisee. */
    public void changeStatus(TransactionStatus target) {
        if (!status.canTransitionTo(target)) {
            throw ApiException.conflict("Transition impossible : " + status + " -> " + target);
        }
        Instant now = Instant.now();
        this.status = target;
        this.updatedAt = now;
        switch (target) {
            case PAID -> this.paidAt = now;
            case COMPLETED -> this.completedAt = now;
            case CANCELLED -> this.cancelledAt = now;
            default -> { }
        }
    }

    public boolean isBuyer(Long userId) {
        return buyerId.equals(userId);
    }

    public boolean isSeller(Long userId) {
        return sellerId.equals(userId);
    }
}
