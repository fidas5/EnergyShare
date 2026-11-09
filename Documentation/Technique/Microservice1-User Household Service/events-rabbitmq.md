# User & Household Service — RabbitMQ Events

## Objectif

RabbitMQ permet à User & Household Service d'informer les autres microservices des changements concernant un foyer, de manière asynchrone, sans bloquer l'utilisateur pendant leur traitement.

User & Household Service publie des événements. Il ne consomme aucun événement des autres services dans cette version : il ne calcule ni la production ni le surplus énergétique.

## Événement 1 — HouseholdCreated

**Producteur :** User & Household Service  
**Consommateurs :** Energy Monitoring Service, AI Optimization Service

**Déclenchement :** après l'enregistrement réussi d'un nouveau foyer.

**Exchange :** `energyshare.events`  
**Routing key :** `household.created`

```json
{
  "eventId": "evt_101",
  "eventType": "HouseholdCreated",
  "householdId": 101,
  "ownerUserId": 12,
  "city": "Nabeul",
  "householdType": "PROSUMER",
  "occurredAt": "2026-11-09T10:00:00Z"
}
```

**Action Energy Monitoring :** préparer le suivi de la production et de la consommation du foyer.  
**Action AI Optimization :** enregistrer le foyer afin de pouvoir lui appliquer les prédictions.

## Événement 2 — SolarInstallationAdded

**Producteur :** User & Household Service  
**Consommateurs :** Energy Monitoring Service, AI Optimization Service

**Déclenchement :** après la déclaration réussie d'une installation solaire.

**Routing key :** `household.installation.added`

```json
{
  "eventId": "evt_102",
  "eventType": "SolarInstallationAdded",
  "installationId": 7,
  "householdId": 101,
  "capacity": 6.5,
  "panelCount": 12,
  "occurredAt": "2026-11-09T10:05:00Z"
}
```

**Action Energy Monitoring :** associer l'installation au foyer pour le suivi de la production.  
**Action AI Optimization :** utiliser la capacité installée dans les prédictions de production.

## Événement 3 — HouseholdDeleted

**Producteur :** User & Household Service  
**Consommateurs :** Energy Marketplace Service, Energy Monitoring Service, AI Optimization Service

**Déclenchement :** après la suppression d'un foyer.

**Routing key :** `household.deleted`

```json
{
  "eventId": "evt_103",
  "eventType": "HouseholdDeleted",
  "householdId": 101,
  "occurredAt": "2026-11-09T11:00:00Z"
}
```

**Action Energy Marketplace :** annuler les offres actives du foyer.  
**Action Energy Monitoring et AI Optimization :** arrêter le suivi et les prédictions pour ce foyer.

## Gestion des événements

- Les événements possèdent un identifiant unique `eventId`.
- Les événements ne contiennent que les données utiles aux consommateurs : l'adresse et les données personnelles de l'utilisateur ne sont pas transmises.
- Les consommateurs doivent éviter les traitements en double.
- Un événement non traité pourra être réessayé.
- Une file de messages morts (Dead Letter Queue) pourra conserver les messages dont le traitement échoue définitivement.
- La publication fiable des événements pourra utiliser le pattern Transactional Outbox : l'événement est enregistré dans la même transaction que le foyer ou l'installation, puis publié.

Les événements et les services consommateurs sont des propositions d'architecture à valider avec l'équipe.

Ces mécanismes sont prévus dans l'architecture et ne sont pas encore implémentés lors de la Validation 1.
