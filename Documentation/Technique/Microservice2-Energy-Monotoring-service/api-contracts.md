# Microservice 2 — Energy Monitoring Service
## Contrats API REST

**Projet :** EnergyShare  
**Service :** `energy-monitoring-service`  
**Stack :** Spring Boot · Spring Web · Spring Data JPA · PostgreSQL  
**Statut :** contrat de conception — endpoints et payloads à implémenter/valider en équipe

## 1. Rôle et conventions

Le service enregistre les **relevés saisis manuellement** par les foyers, calcule les bilans énergétiques et permet la consultation de l'historique et des alertes. Il n'acquiert pas de mesures de compteurs connectés et ne certifie pas l'énergie réellement injectée sur le réseau.

- Préfixe des routes : `/api/energy` (chemin public éventuel ajusté selon la configuration de l'API Gateway).
- Corps d'échange : `application/json` ; dates au format `YYYY-MM-DD`, horodatages ISO 8601.
- Énergie en **kWh** ; valeurs non négatives. Les exemples de décimaux sont illustratifs.
- Authentification : `Authorization: Bearer <JWT>` (Keycloak). Le service doit contrôler les droits sur `householdId` et ne pas se fier uniquement à l'identifiant transmis dans l'URL.
- Références interservices : `householdId` est géré par le **User & Household Service** ; aucune jointure directe avec sa base de données.
- Le surplus dans un bilan est **théorique et déclaratif** : `surplusKwh = max(totalProductionKwh - totalConsumptionKwh, 0)` ; si une représentation du solde net est nécessaire, elle est calculée séparément.

> **Décision de conception :** les endpoints ci-dessous constituent une proposition de contrat. Les noms de routes et la granularité de stockage doivent être alignés avec les autres microservices avant l'implémentation.

## 2. Ressources et DTO

### 2.1 `EnergyReading`

| Propriété | Type | Règle |
|---|---|---|
| `id` | `Long` | Identifiant technique généré par PostgreSQL |
| `householdId` | `String` | Foyer associé, vérifié par l'autorisation métier |
| `readingDate` | `LocalDate` | Date du relevé manuel |
| `productionKwh` | `Double` | Production déclarée, `>= 0` |
| `consumptionKwh` | `Double` | Consommation déclarée, `>= 0` |
| `createdAt` | `LocalDateTime` | Date de création côté serveur |

### 2.2 `EnergyBalance`

| Propriété | Type | Règle |
|---|---|---|
| `id` | `Long` | Identifiant technique |
| `householdId` | `String` | Foyer concerné |
| `periodStart` | `LocalDate` | Date de début incluse |
| `periodEnd` | `LocalDate` | Date de fin incluse |
| `totalProductionKwh` | `Double` | Somme de la production sur la période |
| `totalConsumptionKwh` | `Double` | Somme de la consommation sur la période |
| `surplusKwh` | `Double` | Maximum entre le solde et zéro |

### 2.3 `EnergyAlert`

| Propriété | Type | Règle |
|---|---|---|
| `id` | `Long` | Identifiant technique |
| `householdId` | `String` | Foyer concerné |
| `alertType` | `String` | Ex. `HIGH_CONSUMPTION`, `SURPLUS_AVAILABLE` |
| `message` | `String` | Message explicatif |
| `severity` | `String` | `INFO`, `WARNING` ou `CRITICAL` selon les règles retenues |
| `createdAt` | `LocalDateTime` | Création côté serveur |
| `status` | `String` | `UNREAD`, `READ`, `ARCHIVED` |

## 3. Tableau récapitulatif des routes

| Méthode | Endpoint | Description | Succès |
|---|---|---|---|
| `POST` | `/api/energy/readings` | Ajouter un relevé manuel | `201 Created` |
| `GET` | `/api/energy/readings/{id}` | Consulter un relevé | `200 OK` |
| `GET` | `/api/energy/households/{householdId}/readings` | Historique des relevés d'un foyer (filtres `from`, `to`) | `200 OK` |
| `PUT` | `/api/energy/readings/{id}` | Corriger un relevé déjà saisi | `200 OK` |
| `DELETE` | `/api/energy/readings/{id}` | Supprimer un relevé et invalider les bilans dépendants | `204 No Content` |
| `GET` | `/api/energy/households/{householdId}/balance` | Calculer/consulter un bilan (`from`, `to`) | `200 OK` |
| `GET` | `/api/energy/households/{householdId}/balances` | Historique des bilans enregistrés | `200 OK` |
| `GET` | `/api/energy/households/{householdId}/alerts` | Lister les alertes (`status` facultatif) | `200 OK` |
| `PATCH` | `/api/energy/alerts/{id}/status` | Mettre à jour l'état d'une alerte | `200 OK` |
| `GET` | `/api/energy/households/{householdId}/surplus` | Consulter le surplus déclaré pour une période (usage Marketplace) | `200 OK` |

Les routes de modification/suppression et celles destinées aux autres services sont **des propositions**, et non des fonctionnalités déjà livrées.

## 4. Exemples de requêtes et réponses

### 4.1 Créer un relevé manuel

**`POST /api/energy/readings`**

```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

```json
{
  "householdId": "hh-101",
  "readingDate": "2026-11-10",
  "productionKwh": 20.0,
  "consumptionKwh": 12.0
}
```

**`201 Created`** — en-tête `Location: /api/energy/readings/501`

```json
{
  "id": 501,
  "householdId": "hh-101",
  "readingDate": "2026-11-10",
  "productionKwh": 20.0,
  "consumptionKwh": 12.0,
  "createdAt": "2026-11-10T14:00:00"
}
```

**Validation :** `householdId` obligatoire, quantités positives ou nulles, date valide, foyer accessible à l'utilisateur. Un relevé par foyer et par jour est la règle proposée : second `POST` pour le même couple `(householdId, readingDate)` → `409 Conflict` ; utiliser `PUT` pour corriger l'existant.

### 4.2 Historique des relevés

**`GET /api/energy/households/hh-101/readings?from=2026-11-01&to=2026-11-10`**

**`200 OK`**

```json
[
  {
    "id": 501,
    "householdId": "hh-101",
    "readingDate": "2026-11-10",
    "productionKwh": 20.0,
    "consumptionKwh": 12.0,
    "createdAt": "2026-11-10T14:00:00"
  }
]
```

### 4.3 Calcul du bilan énergétique

**`GET /api/energy/households/hh-101/balance?from=2026-11-10&to=2026-11-10`**

**`200 OK`**

```json
{
  "householdId": "hh-101",
  "periodStart": "2026-11-10",
  "periodEnd": "2026-11-10",
  "totalProductionKwh": 20.0,
  "totalConsumptionKwh": 12.0,
  "surplusKwh": 8.0
}
```

Le DTO de consultation peut omettre `id` si le calcul est effectué à la demande. Si le résultat est matérialisé dans `energy_balances`, `id` est également renvoyé. **Une seule stratégie doit être retenue** pour éviter des divergences.

### 4.4 Surplus communiqué au Marketplace

**`GET /api/energy/households/hh-101/surplus?from=2026-11-10&to=2026-11-10`**

**`200 OK`**

```json
{
  "householdId": "hh-101",
  "periodStart": "2026-11-10",
  "periodEnd": "2026-11-10",
  "surplusKwh": 8.0,
  "basis": "MANUAL_DECLARATION"
}
```

`basis` est un champ **du DTO de réponse proposé**, pas un attribut obligatoire de l'entité JPA. Ce résultat est déclaratif et ne réserve pas l'énergie : Marketplace vérifie et gère lui-même ses offres et réservations. L'accès interservices doit être authentifié et autorisé.

### 4.5 Consulter et marquer une alerte comme lue

**`GET /api/energy/households/hh-101/alerts?status=UNREAD`** → `200 OK`

```json
[
  {
    "id": 801,
    "householdId": "hh-101",
    "alertType": "SURPLUS_AVAILABLE",
    "message": "Un surplus théorique a été identifié sur les relevés déclarés.",
    "severity": "INFO",
    "createdAt": "2026-11-10T14:01:00",
    "status": "UNREAD"
  }
]
```

**`PATCH /api/energy/alerts/801/status`**

```json
{ "status": "READ" }
```

**`200 OK`** : renvoie l'alerte avec `status = "READ"`.

## 5. Erreurs HTTP et format uniforme

| Code | Situation |
|---|---|
| `400 Bad Request` | Champs invalides, valeurs négatives, plage de dates incohérente |
| `401 Unauthorized` | Jeton absent ou invalide |
| `403 Forbidden` | Foyer ou ressource non autorisé |
| `404 Not Found` | Relevé, alerte ou foyer inexistant (selon la politique retenue) |
| `409 Conflict` | Relevé quotidien dupliqué ou conflit de mise à jour |
| `422 Unprocessable Content` | Règle métier non satisfaite, si l'équipe distingue ce cas de `400` |
| `503 Service Unavailable` | Service Household ou dépendance indispensable indisponible |

Exemple de corps d'erreur :

```json
{
  "timestamp": "2026-11-10T14:05:00",
  "status": 409,
  "error": "READING_ALREADY_EXISTS",
  "message": "Un relevé existe déjà pour ce foyer et cette date.",
  "path": "/api/energy/readings"
}
```

## 6. Validations et cas limites

1. `from <= to` ; refuser les périodes incohérentes et plafonner si nécessaire l'étendue des recherches.
2. Ne jamais accepter de valeurs négatives ni de nombres `NaN`/infinis ; vérifier les limites de saisie.
3. Appliquer l'autorisation sur le foyer à **chaque** lecture ou écriture, y compris les accès par identifiant technique.
4. En cas de correction ou suppression d'un relevé, recalculer/invalider les bilans et réévaluer les alertes concernées.
5. Traiter le cas « aucun relevé » explicitement : réponse vide pour l'historique ; pour le bilan, choisir et documenter entre `404`/`204` et un résultat à zéro **marqué comme absence de données**. Ne pas confondre absence de données et production nulle.
6. Les soldes calculés à partir de saisies journalières ne sont pas une garantie de surplus sur une plage horaire précise.
7. Les identifiants d'utilisateur et de foyer proviennent de l'authentification et des droits vérifiés via Household ; les échanges entre services nécessitent leur propre politique de sécurité.

## 7. Tests d'acceptation suggérés

- Création nominale d'un relevé : `201` + champs renvoyés.
- Tentative de création d'un doublon foyer/date : `409`.
- Quantité négative ou date invalide : `400`.
- Consultation d'un autre foyer sans droit : `403`.
- Bilan de 20 kWh produits / 12 kWh consommés : surplus de **8 kWh**.
- Bilan de 7 kWh produits / 12 kWh consommés : surplus de **0 kWh**, et non `-5`.
- Mise à jour d'un relevé : les données agrégées reflètent la correction.
- Indisponibilité de Household au moment d'une vérification requise : réponse contrôlée, sans fuite d'information.
