# Energy Marketplace Service — RabbitMQ Events

## Objectif

RabbitMQ permet de communiquer de manière asynchrone avec Transaction Service sans bloquer l'utilisateur pendant le traitement de sa réservation.

## Événement 1 — ReservationCreated

**Producteur :** Energy Marketplace Service  
**Consommateur :** Transaction Service

**Déclenchement :** après la création réussie d'une réservation.

**Exchange :** `energyshare.events`  
**Routing key :** `reservation.created`

```json
{
  "eventId": "evt_001",
  "eventType": "ReservationCreated",
  "reservationId": "reservation_001",
  "offerId": "offer_001",
  "buyerHouseholdId": "house_202",
  "sellerHouseholdId": "house_101",
  "quantityKwh": 5,
  "pricePerKwh": 0.25,
  "occurredAt": "2026-11-09T12:00:00Z"
}
```

## Événement 2 — TransactionConfirmed

**Producteur :** Transaction Service  
**Consommateur :** Energy Marketplace Service

**Routing key :** `transaction.confirmed`

```json
{
  "eventId": "evt_002",
  "eventType": "TransactionConfirmed",
  "transactionId": "tx_001",
  "reservationId": "reservation_001",
  "status": "CONFIRMED",
  "occurredAt": "2026-11-09T12:01:00Z"
}
```

**Action Marketplace :** modifier la réservation de `PENDING` à `CONFIRMED`. La quantité reste réservée et n'est pas libérée.

## Événement 3 — TransactionCancelled

**Producteur :** Transaction Service  
**Consommateur :** Energy Marketplace Service

**Routing key :** `transaction.cancelled`

```json
{
  "eventId": "evt_003",
  "eventType": "TransactionCancelled",
  "transactionId": "tx_001",
  "reservationId": "reservation_001",
  "reason": "PAYMENT_FAILED",
  "occurredAt": "2026-11-09T12:02:00Z"
}
```

**Action Marketplace :** annuler la réservation et restituer la quantité réservée à l'offre, si elle n'a pas déjà été libérée.

## Gestion des événements

- Les événements possèdent un identifiant unique `eventId`.
- Les consommateurs doivent éviter les traitements en double.
- Un événement non traité pourra être réessayé.
- Une file de messages morts (Dead Letter Queue) pourra conserver les messages dont le traitement échoue définitivement.
- Une réservation `PENDING` expirée doit être annulée selon une politique définie.
- La publication fiable des événements pourra utiliser le pattern Transactional Outbox.

Ces mécanismes sont prévus dans l'architecture et ne sont pas encore implémentés lors de la Validation 1.
