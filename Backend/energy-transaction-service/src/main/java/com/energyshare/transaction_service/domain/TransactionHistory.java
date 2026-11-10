package com.energyshare.transaction_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Une ligne par changement de statut (piste d'audit). */
@Entity
@Table(name = "transaction_history", indexes = @Index(name = "idx_history_tx", columnList = "transactionId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus toStatus;

    private Long changedBy;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant changedAt;

    public static TransactionHistory of(Long transactionId, TransactionStatus from, TransactionStatus to,
                                        Long changedBy, String reason) {
        TransactionHistory h = new TransactionHistory();
        h.transactionId = transactionId;
        h.fromStatus = from;
        h.toStatus = to;
        h.changedBy = changedBy;
        h.reason = reason;
        h.changedAt = Instant.now();
        return h;
    }
}
