package com.energyshare.transaction_service.repository;

import com.energyshare.transaction_service.domain.TransactionHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {

    List<TransactionHistory> findByTransactionIdOrderByChangedAtAscIdAsc(Long transactionId);
}
