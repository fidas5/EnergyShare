package com.energyshare.transaction_service.dto;

import com.energyshare.transaction_service.domain.PaymentMethod;
import com.energyshare.transaction_service.domain.PaymentStatus;
import com.energyshare.transaction_service.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long paymentId, Long transactionId, BigDecimal amount, PaymentMethod method,
                              PaymentStatus status, String reference, Instant paidAt,
                              TransactionStatus transactionStatus) {
}
