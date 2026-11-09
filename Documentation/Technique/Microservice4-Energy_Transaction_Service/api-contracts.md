# Energy Transaction Service — Contrats REST API

## 1. Objectif et périmètre

Le Transaction Service gère le cycle de vie des échanges d'énergie entre un foyer **vendeur** (producteur) et un foyer **acheteur** (consommateur). Il intervient **après** la création d'une réservation dans le Marketplace Service : il vérifie cette réservation, calcule le montant, simule le paiement, suit le statut de l'échange, conserve l'historique et délivre un justificatif.

Il ne gère ni les offres, ni les demandes, ni les réservations : ces données appartiennent au Marketplace Service.

| Élément | Valeur |
|---|---|
| Technologie | Java, Spring Boot, Spring Data JPA |
| Base de données | PostgreSQL — `transaction_db` |
| Préfixe via l'API Gateway | `/api/transactions` |
| Format | JSON (UTF-8), dates ISO 8601 en UTC (ex. `2026-11-09T12:00:00Z`) |
| Authentification | JWT émis par Keycloak (`Authorization: Bearer <token>`) |

## 2. Correspondance fonctionnalités → API

| Fonctionnalité | Réalisation |
|---|---|
| F1 — Create Transaction | `POST /api/transactions` |
| F2 — Validate Transaction | Étape interne de F1 : appel au Marketplace (réservation existante, active, appartenant à l'acheteur, pas déjà utilisée) |
| F3 — Calculate Amount | Étape interne de F1 : `totalAmount = quantityKwh × unitPrice` |
| F4 — Process Payment | `POST /api/transactions/{id}/payments` (paiement simulé) |
| F5 — Confirm Transaction | Automatique après un paiement réussi (`PENDING → CONFIRMED`) ; la clôture (`CONFIRMED → COMPLETED`) se fait avec `PATCH /api/transactions/{id}/complete` |
| F6 — Cancel Transaction | `PATCH /api/transactions/{id}/cancel` |
| F7 — Track Transaction | `GET /api/transactions/{id}` |
| F8 — Transaction History | `GET /api/transactions` (liste filtrée) et `GET /api/transactions/{id}/history` (changements de statut) |
| F9 — Generate Receipt | `GET /api/transactions/{id}/receipt` |
| F10 — Handle Payment Failure | Automatique quand le paiement est refusé : transaction `FAILED` + événement `TransactionFailed` qui libère la réservation côté Marketplace |

## 3. Vue d'ensemble des endpoints

| Méthode | URI | Description | Qui peut l'appeler | Succès | Erreurs possibles |
|---|---|---|---|---|---|
| POST | `/api/transactions` | Créer une transaction à partir d'une réservation | Acheteur | 201 | 400, 401, 403, 404, 409, 503 |
| GET | `/api/transactions/{id}` | Consulter une transaction | Acheteur, vendeur, admin | 200 | 401, 403, 404 |
| GET | `/api/transactions` | Lister les transactions (filtres, pagination) | Tout utilisateur authentifié (limité à son foyer), admin (global) | 200 | 400, 401, 403 |
| POST | `/api/transactions/{id}/payments` | Payer la transaction (paiement simulé) | Acheteur | 200 | 400, 401, 402, 403, 404, 409, 503 |
| PATCH | `/api/transactions/{id}/complete` | Clôturer l'échange (énergie livrée) | Vendeur | 200 | 401, 403, 404, 409 |
| PATCH | `/api/transactions/{id}/cancel` | Annuler la transaction | Acheteur, vendeur | 200 | 401, 403, 404, 409 |
| GET | `/api/transactions/{id}/receipt` | Générer le justificatif d'échange | Acheteur, vendeur, admin | 200 | 401, 403, 404, 409 |
| GET | `/api/transactions/{id}/history` | Consulter l'historique des statuts | Acheteur, vendeur, admin | 200 | 401, 403, 404 |

Il n'existe volontairement aucun endpoint de suppression : les transactions, paiements et historiques sont conservés.

## 4. Détail des endpoints

### 4.1 Créer une transaction — `POST /api/transactions`

Le client n'envoie que l'identifiant de la réservation. Le vendeur, l'acheteur, la quantité et le prix sont **lus auprès du Marketplace** et jamais fournis par le client : cela évite qu'un utilisateur manipule le montant ou usurpe un foyer.

Requête :
```json
{
  "reservationId": "res_1001"
}
```

Réponse — `201 Created` (en-tête `Location: /api/transactions/1`) :
```json
{
  "id": 1,
  "reservationId": "res_1001",
  "sellerHouseholdId": "house_101",
  "buyerHouseholdId": "house_202",
  "quantityKwh": 5.0,
  "unitPrice": 0.25,
  "totalAmount": 1.25,
  "status": "PENDING",
  "createdAt": "2026-11-09T12:00:00Z",
  "completedAt": null,
  "cancellationReason": null
}
```

Contrôles effectués, dans cet ordre :
1. Le JWT est valide et le foyer de l'utilisateur correspond à l'acheteur de la réservation (sinon `403`).
2. La réservation existe auprès du Marketplace (sinon `404 RESERVATION_NOT_FOUND`) et est au statut `PENDING` (sinon `409 RESERVATION_NOT_ACTIVE`).
3. Aucune transaction n'existe déjà pour cette réservation (sinon `409 RESERVATION_ALREADY_USED`). Cette règle est garantie par une contrainte d'unicité en base : deux requêtes simultanées ne peuvent pas créer deux transactions.
4. Le montant est calculé, la transaction est enregistrée avec le statut `PENDING` et la première ligne d'historique est écrite dans la même transaction de base de données.

### 4.2 Consulter une transaction — `GET /api/transactions/{id}`

Réponse — `200 OK` : même représentation que ci-dessus, avec le statut courant. Exemple d'une transaction clôturée :

```json
{
  "id": 1,
  "reservationId": "res_1001",
  "sellerHouseholdId": "house_101",
  "buyerHouseholdId": "house_202",
  "quantityKwh": 5.0,
  "unitPrice": 0.25,
  "totalAmount": 1.25,
  "status": "COMPLETED",
  "createdAt": "2026-11-09T12:00:00Z",
  "completedAt": "2026-11-09T14:30:00Z",
  "cancellationReason": null
}
```

Valeurs possibles de `status` : `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `FAILED`.

### 4.3 Lister les transactions — `GET /api/transactions`

Paramètres de requête (tous optionnels) :

| Paramètre | Description |
|---|---|
| `status` | Filtre sur le statut |
| `role` | `buyer` ou `seller` : ne garder que les transactions où le foyer de l'utilisateur joue ce rôle |
| `from`, `to` | Période sur `createdAt` (ISO 8601) |
| `buyerHouseholdId`, `sellerHouseholdId` | Réservés aux administrateurs |
| `page`, `size` | Pagination (défaut `0` et `20`, `size` maximum `100`) |
| `sort` | Tri, par défaut `createdAt,desc` |

Un utilisateur standard ne voit que les transactions de son propre foyer (comme acheteur ou vendeur) ; les filtres s'appliquent à l'intérieur de ce périmètre.

Exemple : `GET /api/transactions?status=COMPLETED&role=buyer&page=0&size=20`

Réponse — `200 OK` :
```json
{
  "content": [
    {
      "id": 1,
      "reservationId": "res_1001",
      "sellerHouseholdId": "house_101",
      "buyerHouseholdId": "house_202",
      "quantityKwh": 5.0,
      "unitPrice": 0.25,
      "totalAmount": 1.25,
      "status": "COMPLETED",
      "createdAt": "2026-11-09T12:00:00Z",
      "completedAt": "2026-11-09T14:30:00Z",
      "cancellationReason": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 4.4 Payer une transaction — `POST /api/transactions/{id}/payments`

Déclenche le paiement **simulé** d'une transaction `PENDING`. Seul l'acheteur peut payer.

Requête (corps facultatif, `SIMULATED` par défaut) :
```json
{
  "paymentMethod": "SIMULATED"
}
```

Dans cette version, seule la méthode `SIMULATED` est acceptée. `CREDIT_CARD` et `BANK_TRANSFER` existent dans le modèle pour une évolution future et sont refusées avec `400`.

**Paiement accepté** — `200 OK` : le paiement passe à `SUCCESS`, la transaction passe automatiquement à `CONFIRMED` (F5) et l'événement `TransactionConfirmed` est publié.
```json
{
  "paymentId": 1,
  "transactionId": 1,
  "amount": 1.25,
  "paymentMethod": "SIMULATED",
  "paymentStatus": "SUCCESS",
  "paymentReference": "SIM-20261109-000001",
  "paymentDate": "2026-11-09T12:01:00Z",
  "transactionStatus": "CONFIRMED"
}
```

**Paiement refusé** — `402 Payment Required` (F10) : le paiement est enregistré en `FAILED`, la transaction passe à `FAILED` et l'événement `TransactionFailed` est publié pour que le Marketplace libère la réservation.
```json
{
  "timestamp": "2026-11-09T12:01:00Z",
  "status": 402,
  "code": "PAYMENT_REFUSED",
  "message": "Le paiement a été refusé. La transaction est passée à l'état FAILED et la réservation sera libérée.",
  "path": "/api/transactions/1/payments"
}
```

Une panne **technique** du simulateur de paiement n'est pas un refus : le service répond `503 PAYMENT_SERVICE_ERROR`, la transaction reste `PENDING` et le paiement peut être retenté tant que la transaction n'a pas expiré.

Si la transaction n'est plus `PENDING` (déjà payée, annulée, expirée…), la réponse est `409 INVALID_STATUS_TRANSITION`. Un double paiement est donc impossible.

### 4.5 Clôturer l'échange — `PATCH /api/transactions/{id}/complete`

Le foyer vendeur déclare l'énergie livrée. Transition autorisée : `CONFIRMED → COMPLETED`. Dans cette version, la clôture est déclenchée par appel API ; elle pourra à terme être pilotée par les relevés du Energy Production Service.

Réponse — `200 OK` :
```json
{
  "id": 1,
  "status": "COMPLETED",
  "completedAt": "2026-11-09T14:30:00Z"
}
```

L'événement `TransactionCompleted` est publié. Sur une transaction qui n'est pas `CONFIRMED` : `409 INVALID_STATUS_TRANSITION`.

### 4.6 Annuler une transaction — `PATCH /api/transactions/{id}/cancel`

Autorisée pour l'acheteur ou le vendeur, uniquement si la transaction est `PENDING` ou `CONFIRMED`. Le motif est déduit de l'appelant (`BUYER_REQUEST` ou `SELLER_REQUEST`) ; le commentaire est facultatif (255 caractères maximum) et est conservé dans l'historique.

Requête (corps facultatif) :
```json
{
  "comment": "Je n'ai plus besoin de cette énergie"
}
```

Réponse — `200 OK` (exemple d'une transaction déjà payée, donc remboursée de manière simulée) :
```json
{
  "id": 1,
  "status": "CANCELLED",
  "cancellationReason": "BUYER_REQUEST",
  "paymentStatus": "REFUNDED"
}
```

`paymentStatus` vaut `null` si aucun paiement n'avait eu lieu. L'événement `TransactionCancelled` est publié et le Marketplace libère la quantité réservée. Une transaction `COMPLETED`, `CANCELLED` ou `FAILED` ne peut pas être annulée : `409 INVALID_STATUS_TRANSITION`.

Motifs système possibles (non déclenchés par un appel API) : `PAYMENT_TIMEOUT`, `RESERVATION_CANCELLED`, `RESERVATION_EXPIRED`.

### 4.7 Justificatif d'échange — `GET /api/transactions/{id}/receipt`

Disponible uniquement pour une transaction `COMPLETED` (sinon `409 INVALID_STATUS_TRANSITION`). Le justificatif est calculé à la demande à partir des données de la transaction et du paiement ; il n'est pas stocké.

Réponse — `200 OK` :
```json
{
  "receiptNumber": "RCT-2026-000001",
  "transactionId": 1,
  "reservationId": "res_1001",
  "sellerHouseholdId": "house_101",
  "buyerHouseholdId": "house_202",
  "quantityKwh": 5.0,
  "unitPrice": 0.25,
  "totalAmount": 1.25,
  "paymentMethod": "SIMULATED",
  "paymentReference": "SIM-20261109-000001",
  "transactionDate": "2026-11-09T12:00:00Z",
  "completedAt": "2026-11-09T14:30:00Z",
  "status": "COMPLETED",
  "issuedAt": "2026-11-09T15:00:00Z"
}
```

Le justificatif référence les foyers par leur identifiant. Leurs noms d'affichage pourront être ajoutés ultérieurement en interrogeant le User Service.

### 4.8 Historique des statuts — `GET /api/transactions/{id}/history`

Retourne tous les changements de statut, du plus ancien au plus récent.

Réponse — `200 OK` :
```json
[
  {
    "previousStatus": null,
    "newStatus": "PENDING",
    "description": "Transaction créée à partir de la réservation res_1001",
    "changedAt": "2026-11-09T12:00:00Z"
  },
  {
    "previousStatus": "PENDING",
    "newStatus": "CONFIRMED",
    "description": "Paiement simulé accepté (SIM-20261109-000001)",
    "changedAt": "2026-11-09T12:01:00Z"
  },
  {
    "previousStatus": "CONFIRMED",
    "newStatus": "COMPLETED",
    "description": "Échange clôturé par le foyer vendeur",
    "changedAt": "2026-11-09T14:30:00Z"
  }
]
```

## 5. Dépendance sortante : Marketplace Service

Pour valider une réservation (F2), le Transaction Service appelle le Marketplace de façon synchrone. Ce contrat est une **hypothèse de travail à valider avec l'équipe Marketplace**.

`GET /reservations/{reservationId}` — réponse attendue :
```json
{
  "id": "res_1001",
  "offerId": "offer_501",
  "buyerHouseholdId": "house_202",
  "sellerHouseholdId": "house_101",
  "quantityKwh": 5.0,
  "pricePerKwh": 0.25,
  "status": "PENDING"
}
```

Correspondance : `pricePerKwh` (Marketplace) devient `unitPrice` (Transaction). Seul le statut `PENDING` autorise la création d'une transaction.

Résilience : délai maximal de 3 secondes, 2 nouvelles tentatives avec attente croissante, puis disjoncteur (circuit breaker). Si le Marketplace reste injoignable, le service répond `503 MARKETPLACE_UNAVAILABLE` et **n'enregistre rien**. L'appel est authentifié par un compte de service Keycloak (client credentials).

## 6. Gestion des erreurs

Toutes les erreurs ont le même format :
```json
{
  "timestamp": "2026-11-09T12:00:00Z",
  "status": 409,
  "code": "RESERVATION_ALREADY_USED",
  "message": "Une transaction existe déjà pour la réservation res_1001.",
  "path": "/api/transactions"
}
```

| HTTP | `code` | Situation |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Corps ou paramètres invalides (champ manquant, méthode de paiement non supportée, `size` trop grand…) |
| 401 | `UNAUTHENTICATED` | JWT absent, expiré ou invalide |
| 402 | `PAYMENT_REFUSED` | Paiement refusé ; transaction passée à `FAILED` |
| 403 | `ACCESS_DENIED` | L'utilisateur n'est ni acheteur ni vendeur concerné, ou n'a pas le rôle requis pour cette action |
| 404 | `TRANSACTION_NOT_FOUND` | Transaction inexistante |
| 404 | `RESERVATION_NOT_FOUND` | Réservation inexistante côté Marketplace |
| 409 | `RESERVATION_ALREADY_USED` | Une transaction existe déjà pour cette réservation |
| 409 | `RESERVATION_NOT_ACTIVE` | Réservation expirée, annulée ou déjà confirmée |
| 409 | `INVALID_STATUS_TRANSITION` | Action impossible dans l'état courant (annuler une transaction `COMPLETED`, payer deux fois, justificatif sur une transaction non terminée…) ou modification concurrente détectée |
| 503 | `MARKETPLACE_UNAVAILABLE` | Marketplace injoignable ou disjoncteur ouvert |
| 503 | `PAYMENT_SERVICE_ERROR` | Erreur technique du simulateur de paiement ; la transaction reste `PENDING` |

## 7. Sécurité

- Tous les endpoints exigent un JWT valide émis par Keycloak. L'API Gateway le vérifie, et le Transaction Service le **revalide** : il ne fait pas confiance aux seules vérifications de la passerelle.
- Le foyer de l'utilisateur est lu dans le **token** (claim `householdId`, à faire configurer côté Keycloak, hypothèse à confirmer avec l'équipe User Service). Aucun identifiant de foyer envoyé dans le corps ou l'URL n'est considéré comme une preuve d'autorisation.
- Rôles : l'utilisateur standard agit pour son foyer ; le rôle `ADMIN` est en lecture seule sur toutes les transactions.
- Règle d'accès : seuls l'acheteur et le vendeur d'une transaction (et les administrateurs en lecture) peuvent la consulter, ainsi que son historique et son justificatif. Toute autre tentative reçoit `403`.
- Les montants ne sont jamais fournis par le client : ils proviennent du Marketplace et sont calculés par le service.
