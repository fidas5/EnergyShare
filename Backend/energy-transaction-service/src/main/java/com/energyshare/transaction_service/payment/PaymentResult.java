package com.energyshare.transaction_service.payment;

public record PaymentResult(boolean success, String reference, String failureReason) {

    public static PaymentResult success(String reference) {
        return new PaymentResult(true, reference, null);
    }

    public static PaymentResult declined(String reason) {
        return new PaymentResult(false, null, reason);
    }
}
