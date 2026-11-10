package com.energyshare.transaction_service.security;

/** Utilisateur authentifie par l'API Gateway (identite transmise via les en-tetes X-User-*). */
public record AuthenticatedUser(Long userId, Long householdId, boolean admin) {
}
