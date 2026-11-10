package com.energyshare.transaction_service.dto;

import com.energyshare.transaction_service.domain.TransactionHistory;
import com.energyshare.transaction_service.domain.TransactionStatus;
import java.time.Instant;

public record HistoryEntryResponse(TransactionStatus fromStatus, TransactionStatus toStatus,
                                   Long changedBy, String reason, Instant changedAt) {

    public static HistoryEntryResponse from(TransactionHistory h) {
        return new HistoryEntryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(), h.getReason(), h.getChangedAt());
    }
}
