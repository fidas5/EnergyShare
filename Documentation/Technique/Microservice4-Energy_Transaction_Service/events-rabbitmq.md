# Energy Transaction Service — Événements RabbitMQ

## 1. Objectif

La création d'une transaction et son paiement se font de manière synchrone (voir `api-contracts.md`). RabbitMQ sert à **propager le résultat** aux autres services sans les bloquer et sans couplage direct :

- le **Marketplace Service** doit savoir si une réservation est confirmée ou doit être libérée ;
- l'**AI Service** peut exploiter les échanges terminés pour la prédiction de consommation et l'optimisation ;
- le Transaction Service doit être prévenu quand le Marketplace annule ou fait expirer une réservation avant paiement.

Cette approche correspond à une **saga chorégraphiée** : chaque service réagit aux événements des autres, et la compensation d'un échec de paiement consiste à libérer la réservation.

## 2. Vue d'ensemble des événements

| Événement | Sens | Routing key | Producteur → Consommateur | Effet attendu |
|---|---|---|---|---|
| `TransactionConfirmed` | Publié | `transaction.confirmed` | Transaction → Marketplace | La réservation passe de `PENDING` à `CONFIRMED` ; la quantité reste réservée |
| `TransactionCompleted` | Publié | `transaction.completed` | Transaction → AI Service | Enregistrement de l'échange terminé pour l'analyse |
| `TransactionCancelled` | Publié | `transaction.cancelled` | Transaction → Marketplace | Réservation annulée, quantité restituée à l'offre |
| `TransactionFailed` | Publié | `transaction.failed` | Transaction → Marketplace | Réservation libérée, quantité restituée à l'offre (paiement refusé) |
| `ReservationCancelled` | Consommé | `reservation.cancelled` | Marketplace → Transaction | Annulation de la transaction si elle est encore `PENDING` |

## 3. Topologie RabbitMQ

- **Exchange** : `energyshare.events`, de type `topic`, durable, commun à tous les services EnergyShare.
- **Messages** : JSON persistants (`delivery_mode = 2`), `eventId` repris dans l'en-tête `message_id`.

| File | Propriétaire | Bindings | Remarque |
|---|---|---|---|
| `transaction.reservation-events` | Transaction Service | `reservation.cancelled` | File durable, avec dead-letter exchange `energyshare.events.dlx` |
| `transaction.reservation-events.dlq` | Transaction Service | via `energyshare.events.dlx` | Messages en échec définitif |
| `marketplace.transaction-events` | Marketplace Service | `transaction.confirmed`, `transaction.cancelled`, `transaction.failed` | Nom indicatif : chaque consommateur déclare sa propre file |
| `ai.transaction-events` | AI Service | `transaction.completed` | Nom indicatif |

Chaque consommateur possède sa propre file : un événement est ainsi livré à chaque service intéressé, indépendamment des autres.

## 4. Format commun des messages

Tous les événements partagent les mêmes champs d'en-tête :

| Champ | Description |
|---|---|
| `eventId` | UUID unique de l'événement (sert à l'idempotence) |
| `eventType` | Nom de l'événement |
| `eventVersion` | Version du schéma (actuellement `1`), pour faire évoluer les messages sans casser les consommateurs |
| `occurredAt` | Date de l'événement, ISO 8601 UTC |

Les identifiants du Marketplace et des foyers (`reservationId`, `...HouseholdId`) sont des chaînes opaques ; `transactionId` est un entier propre au Transaction Service.

## 5. Événements publiés

### 5.1 TransactionConfirmed

Publié quand le paiement simulé est accepté (`PENDING → CONFIRMED`).

**Routing key :** `transaction.confirmed`

```json
{
  "eventId": "b7e4c1a2-5f3d-4e8a-9c21-0d6f7a1b2c3d",
  "eventType": "TransactionConfirmed",
  "eventVersion": 1,
  "occurredAt": "2026-11-09T12:01:00Z",
  "transactionId": 1,
  "reservationId": "res_1001",
  "sellerHouseholdId": "house_101",
  "buyerHouseholdId": "house_202",
  "quantityKwh": 5.0,
  "totalAmount": 1.25,
  "status": "CONFIRMED"
}
```

**Action du Marketplace :** passer la réservation de `PENDING` à `CONFIRMED`. La quantité reste réservée et n'est pas restituée à l'offre.

### 5.2 TransactionCompleted

Publié quand le vendeur clôture l'échange (`CONFIRMED → COMPLETED`).

**Routing key :** `transaction.completed`

```json
{
  "eventId": "3f8a6d90-1c42-4b7e-8d15-6a9e2f0c7b44",
  "eventType": "TransactionCompleted",
  "eventVersion": 1,
  "occurredAt": "2026-11-09T14:30:00Z",
  "transactionId": 1,
  "reservationId": "res_1001",
  "sellerHouseholdId": "house_101",
  "buyerHouseholdId": "house_202",
  "quantityKwh": 5.0,
  "totalAmount": 1.25,
  "completedAt": "2026-11-09T14:30:00Z",
  "status": "COMPLETED"
}
```

**Action de l'AI Service :** enregistrer l'échange réalisé dans ses données d'analyse. Le Marketplace n'a rien à faire sur cet événement.

### 5.3 TransactionCancelled

Publié quand une transaction `PENDING` ou `CONFIRMED` est annulée par l'acheteur, par le vendeur ou par le système (expiration du paiement).

**Routing key :** `transaction.cancelled`

```json
{
  "eventId": "9d2b7e15-84a6-4c03-b1f9-5e7a3c8d0a12",
  "eventType": "TransactionCancelled",
  "eventVersion": 1,
  "occurredAt": "2026-11-09T12:20:00Z",
  "transactionId": 1,
  "reservationId": "res_1001",
  "previousStatus": "PENDING",
  "reason": "PAYMENT_TIMEOUT",
  "refunded": false,
  "status": "CANCELLED"
}
```

Valeurs de `reason` : `BUYER_REQUEST`, `SELLER_REQUEST`, `PAYMENT_TIMEOUT`. `refunded` vaut `true` si la transaction était déjà payée (remboursement simulé).

**Action du Marketplace :** annuler la réservation et restituer la quantité à l'offre, si elle n'a pas déjà été libérée.

### 5.4 TransactionFailed

Publié quand le paiement est **refusé** (F10).

**Routing key :** `transaction.failed`

```json
{
  "eventId": "e1c5a7b3-92d4-4f68-a0b7-3d8f6c1e9a25",
  "eventType": "TransactionFailed",
  "eventVersion": 1,
  "occurredAt": "2026-11-09T12:01:00Z",
  "transactionId": 1,
  "reservationId": "res_1001",
  "reason": "PAYMENT_REFUSED",
  "status": "FAILED"
}
```

**Action du Marketplace :** libérer la réservation et restituer la quantité à l'offre, si elle n'a pas déjà été libérée.

## 6. Événement consommé

### 6.1 ReservationCancelled

Publié par le Marketplace quand une réservation est annulée ou expire avant d'avoir été confirmée.

**Routing key :** `reservation.cancelled`

```json
{
  "eventId": "5a9c3e71-6b08-4d2f-9e44-1f7b8a0d6c93",
  "eventType": "ReservationCancelled",
  "eventVersion": 1,
  "occurredAt": "2026-11-09T12:05:00Z",
  "reservationId": "res_1001",
  "reason": "EXPIRED"
}
```

Valeurs de `reason` : `EXPIRED` ou `CANCELLED`.

**Action du Transaction Service :**

| Situation | Traitement |
|---|---|
| Aucune transaction pour cette réservation | Événement ignoré (message acquitté) |
| Transaction `PENDING` | Passage à `CANCELLED` (motif `RESERVATION_EXPIRED` ou `RESERVATION_CANCELLED`) et ligne ajoutée à l'historique. **Aucun événement n'est republié** : le Marketplace est déjà à l'origine de l'annulation et a déjà libéré la quantité. |
| Transaction déjà `CONFIRMED`, `COMPLETED`, `CANCELLED` ou `FAILED` | Événement ignoré et alerte journalisée (une réservation payée ne doit pas être annulée par le Marketplace : incohérence à examiner) |

## 7. Scénarios

**Paiement accepté.** L'acheteur crée la transaction (`PENDING`) puis la paie. La transaction passe à `CONFIRMED`, `TransactionConfirmed` est publié, le Marketplace confirme la réservation. Plus tard, le vendeur clôture l'échange : `TransactionCompleted` est publié pour l'AI Service.

**Paiement refusé.** La transaction passe à `FAILED` et `TransactionFailed` est publié. Le Marketplace libère la réservation et restitue la quantité à l'offre.

**Annulation par un participant.** `TransactionCancelled` est publié ; le Marketplace annule la réservation et restitue la quantité.

**Transaction jamais payée.** Après 15 minutes (délai paramétrable) sans paiement, une tâche planifiée passe la transaction à `CANCELLED` (motif `PAYMENT_TIMEOUT`) et publie `TransactionCancelled`. Si le Marketplace fait entre-temps expirer la réservation, c'est `ReservationCancelled` qui annule la transaction.

## 8. Garanties de fiabilité

- **Publication fiable (Transactional Outbox).** Quand le statut d'une transaction change, l'événement correspondant est écrit dans la table `outbox_events` **dans la même transaction de base de données** que le changement de statut. Un relais lit ensuite cette table, publie sur RabbitMQ puis marque l'événement comme publié. Si RabbitMQ est indisponible, rien n'est perdu : la publication reprend dès son retour.
- **Livraison au moins une fois.** Un événement peut donc être livré plusieurs fois. Les consommateurs doivent être idempotents.
- **Idempotence côté Transaction.** Le `eventId` de chaque message traité est enregistré dans `processed_events` ; un doublon est ignoré. Les transitions d'état fournissent une seconde protection : annuler une transaction déjà `CANCELLED` ne produit aucun effet.
- **Reprise et messages morts.** En cas d'échec de traitement, le message est retenté 3 fois avec attente croissante, puis envoyé dans la file de messages morts (`...dlq`) pour analyse manuelle.
- **Ordre.** L'ordre de livraison n'est pas garanti entre événements différents. Chaque consommateur se base donc sur l'état courant de l'objet (et non sur l'ordre d'arrivée) pour décider quoi faire d'un message.
- **Boucles.** Quand le Marketplace réagit à `TransactionCancelled` ou `TransactionFailed` en annulant sa réservation, il peut republier `ReservationCancelled`. Le Transaction Service l'ignore, car la transaction est déjà dans un état terminal.

## 9. Hypothèses à valider avec les autres équipes

- Le Marketplace publie un événement `ReservationCancelled` (routing key `reservation.cancelled`) lors d'une annulation ou d'une expiration de réservation.
- Le Marketplace consomme `transaction.confirmed`, `transaction.cancelled` et `transaction.failed`, et ses traitements sont idempotents.
- L'AI Service est intéressé par `transaction.completed` (à confirmer avec son responsable).
- L'exchange `energyshare.events` est partagé par tous les services, avec la convention de routing keys `<domaine>.<événement>`.

## 10. Statut d'implémentation

Les mécanismes décrits en section 8 (outbox, idempotence, reprise, file de messages morts, expiration) sont prévus dans l'architecture et ne sont pas encore implémentés lors de la Validation 1.
