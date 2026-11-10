# Energy Monitoring Service — RabbitMQ Events

## Objectif

RabbitMQ permet au microservice Energy Monitoring de communiquer de manière asynchrone avec les autres microservices d'EnergyShare.

Après l'enregistrement des mesures et le calcul des bilans énergétiques, Monitoring publie des événements pour informer les services concernés sans les bloquer.

**Technologie :** RabbitMQ, Spring AMQP  
**Exchange :** `energyshare.events`  
**Type d'exchange :** `topic`

## Événement 1 — EnergyReadingCreated

**Producteur :** Energy Monitoring Service  
**Consommateur :** AI Energy Optimization Service

**Déclenchement :** après l'enregistrement réussi d'une mesure de production ou de consommation.

**Routing key :** `monitoring.reading.created`

Exemple JSON :

```json
{
  "eventId": "evt_001",
  "eventType": "EnergyReadingCreated",
  "readingId": 1,
  "householdId": "house_101",
  "installationId": "installation_001",
  "type": "PRODUCTION",
  "valueKwh": 12.5,
  "measuredAt": "2026-11-10T09:00:00Z",
  "source": "SENSOR",
  "occurredAt": "2026-11-10T09:01:00Z"
}
```

**Action AI Optimization :** utiliser les nouvelles mesures pour actualiser les données nécessaires aux prévisions et aux recommandations énergétiques.

## Événement 2 — EnergyBalanceUpdated

**Producteur :** Energy Monitoring Service  
**Consommateur :** AI Energy Optimization Service

**Déclenchement :** après le calcul et l'enregistrement d'un nouveau bilan énergétique.

**Routing key :** `monitoring.balance.updated`

Exemple JSON :

```json
{
  "eventId": "evt_002",
  "eventType": "EnergyBalanceUpdated",
  "householdId": "house_101",
  "totalProductionKwh": 12.5,
  "totalConsumptionKwh": 7.5,
  "surplusKwh": 5.0,
  "deficitKwh": 0.0,
  "periodStart": "2026-11-10T00:00:00Z",
  "periodEnd": "2026-11-11T00:00:00Z",
  "occurredAt": "2026-11-10T09:02:00Z"
}
```

**Action AI Optimization :** exploiter le bilan énergétique pour améliorer les prévisions de production, de consommation et de surplus.

## Événement 3 — EnergySurplusUpdated

**Producteur :** Energy Monitoring Service  
**Consommateurs :** Energy Marketplace Service et AI Energy Optimization Service

**Déclenchement :** après le calcul ou la mise à jour du surplus énergétique d'un foyer.

**Routing key :** `monitoring.surplus.updated`

Exemple JSON :

```json
{
  "eventId": "evt_003",
  "eventType": "EnergySurplusUpdated",
  "householdId": "house_101",
  "surplusKwh": 5.0,
  "periodStart": "2026-11-10T00:00:00Z",
  "periodEnd": "2026-11-11T00:00:00Z",
  "occurredAt": "2026-11-10T09:02:00Z"
}
```

**Action Marketplace :** mettre à jour les informations de surplus connues pour le foyer et utiliser ces informations lors de la vérification d'une nouvelle offre.

**Action AI Optimization :** utiliser le surplus pour proposer des recommandations de partage et d'optimisation énergétique.

L'événement ne crée pas automatiquement une offre dans Marketplace. La publication d'une offre reste une action du foyer dans le microservice Marketplace.

## Configuration des files RabbitMQ

L'exchange `energyshare.events` est partagé entre les microservices.

Chaque consommateur possède sa propre file afin de recevoir indépendamment les événements qui le concernent.

| File | Routing key | Consommateur |
|---|---|---|
| `ai.monitoring.reading.queue` | `monitoring.reading.created` | AI Optimization |
| `ai.monitoring.balance.queue` | `monitoring.balance.updated` | AI Optimization |
| `marketplace.monitoring.surplus.queue` | `monitoring.surplus.updated` | Marketplace |
| `ai.monitoring.surplus.queue` | `monitoring.surplus.updated` | AI Optimization |

## Gestion des événements

- Chaque événement possède un identifiant unique `eventId`.
- Les événements sont publiés uniquement après l'enregistrement réussi des données correspondantes.
- Les consommateurs doivent éviter les traitements en double grâce à `eventId`.
- Un événement non traité pourra être réessayé.
- Une file de messages morts (Dead Letter Queue) pourra conserver les messages dont le traitement échoue définitivement.
- Les événements doivent conserver un format stable et versionnable.
- Les dates sont transmises au format ISO 8601 en UTC.

La publication fiable des événements pourra utiliser le pattern **Transactional Outbox**, afin de garantir la cohérence entre les données enregistrées dans PostgreSQL et les événements à publier dans RabbitMQ.

Ces mécanismes sont prévus dans l'architecture et ne sont pas encore nécessairement implémentés lors de la Validation 1.