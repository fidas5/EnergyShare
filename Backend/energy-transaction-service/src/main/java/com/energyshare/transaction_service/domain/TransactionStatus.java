package com.energyshare.transaction_service.domain;

public enum TransactionStatus {
    PENDING_PAYMENT, PAID, COMPLETED, CANCELLED;

    /** Machine a etats : PENDING_PAYMENT -> PAID -> COMPLETED ; annulation possible avant la cloture. */
    public boolean canTransitionTo(TransactionStatus target) {
        return switch (this) {
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
