package com.energyshare.transaction_service.repository;

import com.energyshare.transaction_service.domain.Transaction;
import com.energyshare.transaction_service.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    boolean existsByReservationIdAndStatusNot(Long reservationId, TransactionStatus status);
}
