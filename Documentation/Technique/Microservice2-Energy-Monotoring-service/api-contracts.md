# Energy Monitoring Service — REST API Contracts

## Objectif

Le microservice Energy Monitoring permet aux foyers de suivre leur production solaire et leur consommation électrique. Il enregistre les relevés énergétiques, calcule les bilans énergétiques et détermine le surplus ou le déficit disponible pour chaque foyer.

**Technologie :** Spring Boot, Spring Data JPA  
**Base de données :** PostgreSQL — `energy_monitoring_db`  
**Port :** `8082`  
**URL logique via API Gateway :** `/api/monitoring`

## Endpoints REST

| Méthode | URI | Description | Réponse |
|---|---|---|---|
| POST | `/readings/production` | Enregistrer une mesure de production solaire | 201, 400 |
| POST | `/readings/consumption` | Enregistrer une mesure de consommation | 201, 400 |
| GET | `/readings/{id}` | Consulter un relevé énergétique | 200, 404 |
| GET | `/readings` | Consulter et filtrer les relevés | 200 |
| GET | `/households/{householdId}/readings` | Consulter les relevés d'un foyer | 200 |
| GET | `/households/{householdId}/balance` | Consulter le bilan énergétique d'un foyer | 200, 404 |
| GET | `/households/{householdId}/surplus` | Consulter le surplus énergétique d'un foyer | 200 |
| GET | `/households/{householdId}/statistics` | Consulter les statistiques énergétiques | 200 |

## Exemple — Enregistrement de la production

**POST `/api/monitoring/readings/production`**

Requête JSON :

```json
{
  "householdId": "house_101",
  "installationId": "installation_001",
  "valueKwh": 12.5,
  "measuredAt": "2026-11-10T09:00:00Z",
  "source": "SENSOR"
}
```

Réponse — `201 Created` :

```json
{
  "id": 1,
  "householdId": "house_101",
  "installationId": "installation_001",
  "type": "PRODUCTION",
  "valueKwh": 12.5,
  "measuredAt": "2026-11-10T09:00:00Z",
  "source": "SENSOR"
}
```

## Exemple — Enregistrement de la consommation

**POST `/api/monitoring/readings/consumption`**

Requête JSON :

```json
{
  "householdId": "house_101",
  "valueKwh": 7.5,
  "measuredAt": "2026-11-10T09:00:00Z",
  "source": "SENSOR"
}
```

Réponse — `201 Created` :

```json
{
  "id": 2,
  "householdId": "house_101",
  "type": "CONSUMPTION",
  "valueKwh": 7.5,
  "measuredAt": "2026-11-10T09:00:00Z",
  "source": "SENSOR"
}
```

## Exemple — Consultation du bilan énergétique

**GET `/api/monitoring/households/house_101/balance?from=2026-11-10T00:00:00Z&to=2026-11-11T00:00:00Z`**

Réponse — `200 OK` :

```json
{
  "householdId": "house_101",
  "periodStart": "2026-11-10T00:00:00Z",
  "periodEnd": "2026-11-11T00:00:00Z",
  "totalProductionKwh": 12.5,
  "totalConsumptionKwh": 7.5,
  "surplusKwh": 5.0,
  "deficitKwh": 0.0
}
```

Le surplus est calculé à partir de la différence entre la production et la consommation sur la période demandée.

## Exemple — Consultation du surplus

**GET `/api/monitoring/households/house_101/surplus?from=2026-11-10T00:00:00Z&to=2026-11-11T00:00:00Z`**

Réponse — `200 OK` :

```json
{
  "householdId": "house_101",
  "periodStart": "2026-11-10T00:00:00Z",
  "periodEnd": "2026-11-11T00:00:00Z",
  "surplusKwh": 5.0
}
```

Le surplus correspond à une quantité calculée sur une période donnée. Il ne représente pas nécessairement une disponibilité instantanée.

## Paramètres de filtrage

L'endpoint `GET /readings` peut accepter les paramètres suivants :

- `householdId` : identifiant du foyer.
- `type` : PRODUCTION ou CONSUMPTION.
- `from` : date de début.
- `to` : date de fin.
- `page` : numéro de page.
- `size` : nombre de résultats par page.

## Codes HTTP

- **200 OK :** opération réussie.
- **201 Created :** relevé créé.
- **400 Bad Request :** données invalides.
- **401 Unauthorized :** authentification absente ou invalide.
- **403 Forbidden :** accès non autorisé.
- **404 Not Found :** relevé ou bilan introuvable.
- **503 Service Unavailable :** dépendance nécessaire indisponible.

## Validation des données

- La quantité énergétique doit être strictement positive.
- Le foyer doit être valide et autorisé.
- La date de mesure doit être valide.
- Le type du relevé doit être PRODUCTION ou CONSUMPTION.
- La source doit être MANUAL, SIMULATED ou SENSOR.
- Les dates de début et de fin doivent respecter l'ordre chronologique.

## Sécurité

Les endpoints nécessitent un JWT valide fourni par Keycloak.

Le backend vérifie également que l'utilisateur est autorisé à consulter ou enregistrer des données pour le foyer concerné.

Les identifiants de foyer transmis dans les requêtes ne constituent pas, à eux seuls, une preuve d'autorisation.

Les communications entre microservices doivent également être sécurisées.