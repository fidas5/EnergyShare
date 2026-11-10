# Microservice 2 — Energy Monitoring Service
## Contrats d'événements RabbitMQ

**Projet :** EnergyShare  
**Service producteur envisagé :** `energy-monitoring-service`  
**Statut :** proposition de conception ; **la communication RabbitMQ du Monitoring n'a pas été arrêtée dans les contrats initiaux du projet**.

## 1. Place de RabbitMQ dans l'architecture

Le service Monitoring expose principalement ses données via **API REST synchrones**. Le microservice **Energy Marketplace Service** vérifie le surplus déclaré auprès de Monitoring par **OpenFeign**, lors de la publication ou de la validation d'une offre. Ce contrôle synchrone n'est **pas remplacé** par RabbitMQ.

RabbitMQ peut, en complément, diffuser des **notifications métier** lorsque des relevés sont saisis/corrigés ou qu'un bilan est recalculé. Les noms et canaux ci-dessous sont proposés pour préparer l'intégration et **ne doivent pas être présentés comme déjà implémentés**.

### Répartition des responsabilités

| Service | Rôle | Canal principal |
|---|---|---|
| Energy Monitoring | Source des relevés déclaratifs et bilans | REST / OpenFeign pour la consultation |
| Energy Monitoring | Publication facultative de changements | RabbitMQ si retenu |
| Energy Marketplace | Vérification du surplus avant une offre | REST / OpenFeign vers Monitoring |
| Energy Marketplace / Transaction | Réservation et confirmation des échanges | Leurs événements RabbitMQ dédiés (`ReservationCreated`, etc.) |

> Monitoring **ne confirme pas les transactions**, **ne bloque pas les kWh** et **ne publie pas** `TransactionConfirmed` ou `TransactionCancelled`.

## 2. Conventions de messagerie proposées

| Paramètre | Valeur proposée |
|---|---|
| Exchange | `energyshare.energy.events` |
| Type | `topic`, durable |
| Routing keys | `energy.reading.created`, `energy.reading.updated`, `energy.reading.deleted`, `energy.balance.updated`, `energy.alert.created` |
| Message | JSON UTF-8 |
| Publisher | Energy Monitoring Service |
| Queues | Nommées par chaque consommateur, ex. `marketplace.energy-updates.q` **si besoin avéré** |
| Dead-letter exchange | `energyshare.energy.dlx` (proposé) |
| Accusé de réception | `ack` après traitement réussi ; `nack` / retry contrôlé sinon |

Éviter une file unique partagée entre plusieurs consommateurs fonctionnels : chaque consommateur doit pouvoir recevoir les messages qui lui sont destinés via sa propre file et ses bindings.

## 3. Enveloppe commune des événements

```json
{
  "eventId": "2e1e4ec1-2adb-41b2-a69f-4511a4790f13",
  "eventType": "EnergyReadingCreated",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T14:00:00Z",
  "producer": "energy-monitoring-service",
  "aggregateId": "501",
  "correlationId": "8e50d29d-869a-493f-8f3c-74570b747e40",
  "payload": {}
}
```

- `eventId` : identifiant unique, utilisé pour détecter les doublons.
- `eventType` : type métier explicite.
- `eventVersion` : version du schéma ; une évolution incompatible implique une nouvelle version.
- `occurredAt` : horodatage UTC, avec fuseau explicite.
- `aggregateId` : identifiant technique du relevé, bilan ou de l'alerte.
- `correlationId` : suivi de bout en bout des requêtes/événements.
- `payload` : uniquement les informations nécessaires aux abonnés ; aucun mot de passe ni donnée personnelle superflue.

## 4. Événements proposés

### 4.1 `EnergyReadingCreated`

**Routing key :** `energy.reading.created`  
**Déclencheur :** un nouveau relevé manuel est enregistré avec succès.

```json
{
  "eventId": "cb6e146f-0c8a-45cb-8fd5-77fcaba635ed",
  "eventType": "EnergyReadingCreated",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T14:00:00Z",
  "producer": "energy-monitoring-service",
  "aggregateId": "501",
  "correlationId": "f89bddaa-b9cd-4613-8ca9-64d31caa9812",
  "payload": {
    "readingId": 501,
    "householdId": "hh-101",
    "readingDate": "2026-11-10",
    "productionKwh": 20.0,
    "consumptionKwh": 12.0
  }
}
```

**Consommateurs possibles :** service de statistiques, notifications ou mécanisme d'invalidation de cache — sous réserve de besoins validés.

### 4.2 `EnergyReadingUpdated`

**Routing key :** `energy.reading.updated`  
**Déclencheur :** correction d'un relevé existant.

```json
{
  "eventId": "8475264f-3940-4136-aed6-0ef6d8d7e9e0",
  "eventType": "EnergyReadingUpdated",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T14:30:00Z",
  "producer": "energy-monitoring-service",
  "aggregateId": "501",
  "correlationId": "f89bddaa-b9cd-4613-8ca9-64d31caa9812",
  "payload": {
    "readingId": 501,
    "householdId": "hh-101",
    "readingDate": "2026-11-10",
    "productionKwh": 18.0,
    "consumptionKwh": 12.0
  }
}
```

**Conséquence :** les bilans dépendants doivent être recalculés et les consommateurs éventuels informés si leurs vues sont périmées.

### 4.3 `EnergyReadingDeleted`

**Routing key :** `energy.reading.deleted`  
**Déclencheur :** suppression autorisée d'un relevé.

```json
{
  "eventId": "c58d289f-07dd-484e-83ee-3e71d65dfb45",
  "eventType": "EnergyReadingDeleted",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T15:00:00Z",
  "producer": "energy-monitoring-service",
  "aggregateId": "501",
  "correlationId": "f89bddaa-b9cd-4613-8ca9-64d31caa9812",
  "payload": {
    "readingId": 501,
    "householdId": "hh-101",
    "readingDate": "2026-11-10"
  }
}
```

### 4.4 `EnergyBalanceUpdated`

**Routing key :** `energy.balance.updated`  
**Déclencheur :** création/recalcul d'un bilan agrégé.

```json
{
  "eventId": "f8ce4257-c554-4d46-b9cf-09192996d404",
  "eventType": "EnergyBalanceUpdated",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T14:01:00Z",
  "producer": "energy-monitoring-service",
  "aggregateId": "901",
  "correlationId": "f89bddaa-b9cd-4613-8ca9-64d31caa9812",
  "payload": {
    "balanceId": 901,
    "householdId": "hh-101",
    "periodStart": "2026-11-10",
    "periodEnd": "2026-11-10",
    "totalProductionKwh": 20.0,
    "totalConsumptionKwh": 12.0,
    "surplusKwh": 8.0,
    "basis": "MANUAL_DECLARATION"
  }
}
```

**Attention :** ce message exprime un **bilan déclaré** ; il ne promet ni disponibilité en temps réel ni transfert physique. Un service destinataire ne doit pas considérer cet événement comme une validation définitive d'une réservation.

### 4.5 `EnergyAlertCreated` (facultatif)

**Routing key :** `energy.alert.created`  
**Déclencheur :** création d'une alerte métier selon les seuils configurés.

```json
{
  "eventId": "42030174-e79c-4cd1-a801-58d595383bf5",
  "eventType": "EnergyAlertCreated",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T14:02:00Z",
  "producer": "energy-monitoring-service",
  "aggregateId": "801",
  "correlationId": "f89bddaa-b9cd-4613-8ca9-64d31caa9812",
  "payload": {
    "alertId": 801,
    "householdId": "hh-101",
    "alertType": "SURPLUS_AVAILABLE",
    "severity": "INFO"
  }
}
```

## 5. Gestion des erreurs et fiabilité

- **Publication après persistance :** éviter de publier un événement si la transaction PostgreSQL a échoué. Pour une garantie accrue, utiliser un **Transactional Outbox** (table `outbox_events` dans la même transaction SQL, puis publication différée).
- **Livraison « au moins une fois » :** prévoir des doublons. Chaque consommateur stocke les `eventId` traités ou applique une mise à jour idempotente.
- **Retries bornés :** mettre en place un nombre limité de tentatives puis une **dead-letter queue** avec visibilité opérationnelle.
- **Ordre des messages :** ne pas supposer un ordre global. Un numéro de version métier par agrégat est conseillé si les mises à jour successives doivent être ordonnées.
- **Traçabilité :** journaliser `correlationId`, `eventId`, routing key et erreurs sans exposer le contenu sensible.
- **Consommateurs absents :** une publication sur un exchange ne garantit pas qu'une file soit liée à la routing key ; contrôler les bindings et les publications non routées.
- **Sécurité :** comptes RabbitMQ dédiés et droits restreints sur exchange/queues ; chiffrement des connexions selon l'environnement.

## 6. Flux à distinguer explicitement

```text
Producteur -> Monitoring (REST) : POST relevé manuel
Monitoring -> PostgreSQL : enregistrement
Monitoring --(RabbitMQ éventuel)--> abonnés : EnergyReadingCreated

Marketplace -> Monitoring (OpenFeign / REST) : GET surplus déclaré
Monitoring -> Marketplace (REST) : surplusKwh + période

Marketplace --(RabbitMQ)--> Transaction : ReservationCreated
Transaction --(RabbitMQ)--> Marketplace : TransactionConfirmed/Cancelled
```

**Conclusion :** RabbitMQ reste un mécanisme de notification éventuel pour Monitoring ; les appels OpenFeign de Marketplace à Monitoring restent synchrones pour les validations qui nécessitent une réponse immédiate.

## 7. Points à valider avec l'équipe

1. Monitoring doit-il réellement **publier** des événements RabbitMQ dès cette phase du projet ? Si non, conserver cette documentation comme proposition d'évolution.
2. Quels microservices doivent consommer chaque type d'événement ? Ne pas créer de queues inutiles.
3. Faut-il matérialiser les bilans en PostgreSQL ou les calculer à la demande ? Cette décision affecte `EnergyBalanceUpdated`.
4. Quelle est la règle de modification des relevés déjà associés à des offres publiées ? Une correction historique ne doit pas invalider silencieusement des transactions déjà confirmées.
