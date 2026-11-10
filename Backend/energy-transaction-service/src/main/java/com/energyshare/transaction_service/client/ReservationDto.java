package com.energyshare.transaction_service.client;

import java.math.BigDecimal;

/** Reservation telle que renvoyee par GET /reservations/{id} du Marketplace Service (contrat suppose). */
public record ReservationDto(Long id, Long buyerId, Long buyerHouseholdId, Long sellerId, Long sellerHouseholdId,
                             BigDecimal energyKwh, BigDecimal pricePerKwh, String status) {
}
