package com.energyshare.transaction_service.dto;

import com.energyshare.transaction_service.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** cardToken : "tok_declined" simule un refus (402), "tok_unavailable" une panne du prestataire (503). */
public record PaymentRequest(@NotNull PaymentMethod method, @Size(max = 100) String cardToken) {
}
