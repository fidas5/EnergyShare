package com.energyshare.transaction_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTransactionRequest(@NotNull @Positive Long reservationId) {
}
