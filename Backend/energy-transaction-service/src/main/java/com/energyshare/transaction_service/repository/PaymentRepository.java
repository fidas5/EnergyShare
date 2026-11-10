package com.energyshare.transaction_service.repository;

import com.energyshare.transaction_service.domain.Payment;
import com.energyshare.transaction_service.domain.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByTransactionIdAndStatusOrderByCreatedAtDesc(Long transactionId, PaymentStatus status);
}
