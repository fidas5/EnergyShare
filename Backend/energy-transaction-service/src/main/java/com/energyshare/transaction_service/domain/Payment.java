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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payment_tx", columnList = "transactionId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 60)
    private String reference;

    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    public static Payment of(Long transactionId, BigDecimal amount, PaymentMethod method,
                             PaymentStatus status, String reference, String failureReason) {
        Payment p = new Payment();
        p.transactionId = transactionId;
        p.amount = amount;
        p.method = method;
        p.status = status;
        p.reference = reference;
        p.failureReason = failureReason;
        p.createdAt = Instant.now();
        return p;
    }
}
