# Energy Marketplace Service — REST API Contracts

## Objectif

Le microservice Energy Marketplace permet aux foyers producteurs de publier des offres d'énergie solaire et aux consommateurs de consulter les offres, créer des demandes et effectuer des réservations.

**Technologie :** Spring Boot, Spring Data MongoDB  
**Base de données :** MongoDB — `marketplace_db`  
**URL logique via API Gateway :** `/api/marketplace`

## Endpoints REST

| Méthode | URI | Description | Réponse |
|---|---|---|---|
| POST | `/offers` | Créer une offre d'énergie | 201 |
| GET | `/offers` | Consulter et filtrer les offres | 200 |
| GET | `/offers/{id}` | Consulter une offre | 200, 404 |
| PUT | `/offers/{id}` | Modifier une offre | 200, 400, 409 |
| DELETE | `/offers/{id}` | Annuler une offre | 204, 404, 409 |
| POST | `/requests` | Créer une demande d'énergie | 201 |
| GET | `/requests/{id}` | Consulter une demande | 200, 404 |
| GET | `/requests` | Lister les demandes | 200 |
| POST | `/reservations` | Réserver une quantité d'énergie | 201, 409 |
| GET | `/reservations/{id}` | Consulter une réservation | 200, 404 |
| DELETE | `/reservations/{id}` | Annuler une réservation | 204, 404, 409 |

## Exemple — Publication d'une offre

**POST `/api/marketplace/offers`**

Requête JSON :

```json
{
  "sellerHouseholdId": "house_101",
  "quantityKwh": 8,
  "pricePerKwh": 0.25,
  "zone": "Nabeul",
  "startTime": "2026-11-10T09:00:00",
  "endTime": "2026-11-10T17:00:00"
}
```

Réponse — `201 Created` :

```json
{
  "id": "offer_001",
  "sellerHouseholdId": "house_101",
  "quantityKwh": 8,
  "remainingKwh": 8,
  "pricePerKwh": 0.25,
  "status": "ACTIVE"
}
```

## Exemple — Réservation

**POST `/api/marketplace/reservations`**

Requête JSON :

```json
{
  "offerId": "offer_001",
  "buyerHouseholdId": "house_202",
  "reservedKwh": 5
}
```

Réponse — `201 Created` :

```json
{
  "id": "reservation_001",
  "offerId": "offer_001",
  "buyerHouseholdId": "house_202",
  "reservedKwh": 5,
  "status": "PENDING"
}
```

## Codes HTTP

- **200 OK :** opération réussie.
- **201 Created :** ressource créée.
- **204 No Content :** annulation effectuée.
- **400 Bad Request :** données invalides.
- **401 Unauthorized :** authentification absente ou invalide.
- **403 Forbidden :** accès non autorisé à la ressource.
- **404 Not Found :** ressource introuvable.
- **409 Conflict :** conflit métier, par exemple quantité insuffisante.
- **503 Service Unavailable :** dépendance nécessaire indisponible.

## Sécurité

Les endpoints nécessitent un JWT valide fourni par Keycloak. Le backend vérifie également que l'utilisateur est autorisé à agir pour le foyer concerné.

Les identifiants de foyer transmis dans les requêtes ne constituent pas, à eux seuls, une preuve d'autorisation.
