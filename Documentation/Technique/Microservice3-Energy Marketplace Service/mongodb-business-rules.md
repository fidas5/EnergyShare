# Energy Marketplace Service — Modèle MongoDB et règles métier

## 1. Choix de la base de données

Nous avons choisi MongoDB pour gérer les offres, les demandes et les réservations d'énergie sous forme de documents.

MongoDB permet de stocker des données flexibles, d'effectuer des recherches et des filtres sur les offres et d'utiliser des mises à jour atomiques pour contrôler les réservations concurrentes.

Chaque microservice possède sa propre base de données. Marketplace n'accède jamais directement aux collections appartenant aux autres services.

**Nom de la base :** `marketplace_db`

## 2. Collections

### Collection `energy_offers`

| Champ | Type | Description |
|---|---|---|
| `_id` | String | Identifiant unique |
| `sellerHouseholdId` | String | Identifiant du foyer vendeur |
| `quantityKwh` | Double | Quantité totale proposée |
| `remainingKwh` | Double | Quantité encore réservable |
| `pricePerKwh` | Decimal128 | Prix unitaire |
| `zone` | String | Zone géographique |
| `startTime` | Date | Début de disponibilité |
| `endTime` | Date | Fin de disponibilité |
| `status` | String | ACTIVE, RESERVED, EXPIRED, CANCELLED |

### Collection `energy_requests`

| Champ | Type | Description |
|---|---|---|
| `_id` | String | Identifiant unique |
| `buyerHouseholdId` | String | Foyer consommateur |
| `requestedKwh` | Double | Quantité recherchée |
| `maxPricePerKwh` | Decimal128 | Prix maximum acceptable |
| `zone` | String | Zone recherchée |
| `status` | String | OPEN, PARTIALLY_SATISFIED, SATISFIED, CANCELLED |

### Collection `energy_reservations`

| Champ | Type | Description |
|---|---|---|
| `_id` | String | Identifiant unique |
| `offerId` | String | Référence à l'offre |
| `requestId` | String, optionnel | Référence à la demande |
| `buyerHouseholdId` | String | Foyer acheteur |
| `reservedKwh` | Double | Quantité réservée |
| `expiresAt` | Date | Date d'expiration |
| `status` | String | PENDING, CONFIRMED, CANCELLED, EXPIRED |

Les références sont conservées sous forme d'identifiants. MongoDB n'impose pas de clés étrangères entre les collections.

Les montants financiers seront manipulés en Java avec BigDecimal. Pour MongoDB, Decimal128 est recommandé pour le stockage des prix.

## 3. Règles métier

**R1 — Publication :** seul un foyer actif et autorisé peut publier une offre.

**R2 — Surplus :** la quantité proposée ne doit pas dépasser le surplus confirmé par Energy Monitoring pour la période concernée.

**R3 — Quantité :** une offre doit contenir une quantité strictement positive.

**R4 — Prix :** le prix doit être supérieur ou égal à zéro.

**R5 — Période :** la date de début doit précéder la date de fin.

**R6 — Réservation :** la quantité demandée ne doit pas dépasser `remainingKwh`.


**R7 — Cohérence :** la création de la réservation et la diminution de la quantité doivent réussir ensemble, via une transaction MongoDB adaptée.

**R8 — Annulation :** une réservation annulée ou expirée libère sa quantité une seule fois.

**R9 — Confirmation :** une transaction confirmée fait passer la réservation à `CONFIRMED`, sans restituer sa quantité.

**R10 — Statuts :** une offre annulée ou expirée ne peut plus recevoir de réservation.

**R11 — Autorisation :** le vendeur ne peut pas réserver sa propre offre.

## 4. Cas de panne

| Panne | Comportement prévu |
|---|---|
| Household indisponible | Refuser temporairement la publication et retourner 503 |
| Energy Monitoring indisponible | Ne pas publier une offre dont le surplus ne peut pas être vérifié |
| AI Service indisponible | Afficher les offres sans classement intelligent |
| RabbitMQ indisponible | Conserver la réservation et son événement à publier ; réessayer ensuite |
| Transaction Service indisponible | Garder la réservation PENDING jusqu'au traitement ou à l'expiration |
| MongoDB indisponible | Retourner une erreur 503, sans confirmer de nouvelle réservation |

## 5. Cohérence et disponibilité

Pour empêcher les doubles réservations, Marketplace doit diminuer la quantité réservable uniquement si le stock est suffisant.

La création d'une réservation et la modification de l'offre doivent être atomiques.

Le service doit également gérer les événements reçus plusieurs fois et éviter de libérer plusieurs fois la même quantité.

Une stratégie de reprise, de compensation et d'expiration est prévue pour les traitements asynchrones.
