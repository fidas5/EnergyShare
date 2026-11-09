# User & Household Service — Modèle PostgreSQL et règles métier

## 1. Choix de la base de données

Nous avons choisi PostgreSQL pour gérer les utilisateurs, les foyers, les membres, les installations solaires et les préférences énergétiques sous forme de tables reliées entre elles.

PostgreSQL convient à ce service car les données sont fortement liées : un utilisateur peut appartenir à plusieurs foyers et un foyer peut avoir plusieurs membres, relation représentée par la table `household_members`. Les clés étrangères et les contraintes d'unicité garantissent l'intégrité des données, et les transactions ACID permettent par exemple de créer un foyer et son premier membre en une seule opération.

Chaque microservice possède sa propre base de données. User & Household est le seul service qui accède à ses tables. Les autres services obtiennent les informations d'un foyer par l'API REST ou par les événements RabbitMQ.

**Nom de la base :** `household_db`

## 2. Tables

Les noms des champs sont ceux du diagramme de classes. Spring Data JPA les associe aux colonnes en `snake_case`.

### Table `users`

| Champ | Type | Description |
|---|---|---|
| `id` | BIGINT | Identifiant unique (clé primaire) |
| `keycloakId` | VARCHAR, unique | Identifiant de l'utilisateur dans Keycloak |
| `firstName` | VARCHAR | Prénom |
| `lastName` | VARCHAR | Nom |
| `email` | VARCHAR, unique | Adresse email |
| `phone` | VARCHAR | Numéro de téléphone |
| `status` | VARCHAR | ACTIVE, INACTIVE |

### Table `households`

| Champ | Type | Description |
|---|---|---|
| `id` | BIGINT | Identifiant unique (clé primaire) |
| `name` | VARCHAR | Nom du foyer |
| `address` | VARCHAR | Adresse |
| `city` | VARCHAR | Ville |
| `householdType` | VARCHAR | PRODUCER, CONSUMER, PROSUMER |
| `createdAt` | DATE | Date de création |

### Table `household_members`

| Champ | Type | Description |
|---|---|---|
| `id` | BIGINT | Identifiant unique (clé primaire) |
| `userId` | BIGINT | Clé étrangère vers `users` |
| `householdId` | BIGINT | Clé étrangère vers `households` |
| `role` | VARCHAR | OWNER, MEMBER |
| `joinedAt` | DATE | Date d'entrée dans le foyer |

Le couple (`userId`, `householdId`) est unique.

### Table `solar_installations`

| Champ | Type | Description |
|---|---|---|
| `id` | BIGINT | Identifiant unique (clé primaire) |
| `householdId` | BIGINT | Clé étrangère vers `households` |
| `capacity` | DOUBLE | Capacité installée en kW |
| `panelCount` | INTEGER | Nombre de panneaux |
| `panelType` | VARCHAR | Type de panneaux |
| `installationDate` | DATE | Date d'installation |
| `status` | VARCHAR | ACTIVE, INACTIVE |

### Table `energy_preferences`

| Champ | Type | Description |
|---|---|---|
| `id` | BIGINT | Identifiant unique (clé primaire) |
| `householdId` | BIGINT, unique | Clé étrangère vers `households` |
| `preferredEnergySource` | VARCHAR | Source d'énergie préférée |
| `maxBudget` | NUMERIC(10,2) | Budget maximum |
| `preferredTime` | VARCHAR | Période de consommation préférée |

### Valeurs des statuts et des types

| Énumération | Valeur | Signification |
|---|---|---|
| `UserStatus` | `ACTIVE` | Compte actif, l'utilisateur peut utiliser la plateforme |
| | `INACTIVE` | Compte désactivé (information seulement) |
| `HouseholdType` | `PRODUCER` | Foyer équipé de panneaux solaires qui vend son surplus |
| | `CONSUMER` | Foyer sans production qui achète de l'énergie |
| | `PROSUMER` | Foyer qui produit, consomme et peut vendre comme acheter |
| `MemberRole` | `OWNER` | Propriétaire du foyer, il le gère et gère ses membres |
| | `MEMBER` | Membre du foyer, il peut le consulter |
| `InstallationStatus` | `ACTIVE` | Installation en service |
| | `INACTIVE` | Installation hors service |

Energy Marketplace s'appuie sur `householdType` : un foyer `PRODUCER` ou `PROSUMER` peut publier des offres, un foyer `CONSUMER` ou `PROSUMER` peut créer des demandes.

Contrairement à MongoDB, PostgreSQL impose les clés étrangères entre les tables. Un foyer ne peut donc pas avoir de membre, d'installation ou de préférence qui référence un identifiant inexistant.

Les montants financiers seront manipulés en Java avec BigDecimal et stockés en NUMERIC.

## 3. Règles métier

**R1 — Email :** l'adresse email d'un utilisateur est unique.

**R2 — Appartenance :** un utilisateur peut appartenir à plusieurs foyers, mais une seule fois par foyer.

**R3 — Création :** un foyer possède toujours au moins un membre. Son créateur devient `OWNER`.

**R4 — Autorisation :** seul l'`OWNER` peut modifier ou supprimer le foyer et gérer ses membres.

**R5 — Consultation :** seuls les membres d'un foyer peuvent consulter ses données, ainsi que les microservices autorisés qui en ont besoin.

**R6 — Propriétaire :** l'`OWNER` ne peut pas être retiré du foyer.

**R7 — Installation :** seul un foyer de type `PRODUCER` ou `PROSUMER` peut déclarer une installation solaire.

**R8 — Capacité :** la capacité et le nombre de panneaux d'une installation doivent être strictement positifs.

**R9 — Date :** la date d'installation ne peut pas être dans le futur.

**R10 — Préférences :** un foyer possède au plus un enregistrement de préférences. Le budget maximum doit être supérieur ou égal à zéro.

**R11 — Suppression :** un foyer ne peut pas être supprimé tant qu'il compte d'autres membres que l'`OWNER`. Sa suppression entraîne celle de son membre `OWNER`, de ses installations et de ses préférences, et déclenche l'événement `HouseholdDeleted`.

**R12 — Publication :** chaque création ou suppression de foyer et chaque ajout d'installation est signalé aux autres services par un événement RabbitMQ.

## 4. Cas de panne

| Panne | Comportement prévu |
|---|---|
| PostgreSQL indisponible | Retourner une erreur 503, sans confirmer de création ni publier d'événement |
| RabbitMQ indisponible | Conserver les données et l'événement à publier ; réessayer ensuite, sans bloquer l'utilisateur |
| Keycloak indisponible | Refuser les requêtes dont le JWT ne peut pas être vérifié (401) |
| Energy Marketplace indisponible | Aucun impact sur User & Household, qui ne dépend pas de ce service |
| Energy Monitoring ou AI Optimization indisponible | Les événements restent dans leur file et sont traités à leur retour |

## 5. Cohérence et disponibilité

La création d'un foyer et de son membre `OWNER` doit se faire dans une seule transaction : les deux réussissent ou aucun n'est enregistré. Il en va de même pour la suppression d'un foyer et de ses données associées.

Les contraintes d'unicité de la base (email, couple utilisateur–foyer) empêchent les doublons même si deux requêtes arrivent en même temps.

Pour ne pas perdre d'événement, l'événement est enregistré dans la même transaction que la donnée métier (Transactional Outbox), puis publié sur RabbitMQ.

User & Household ne consomme pas d'événement des autres services. Les consommateurs de ses événements doivent, eux, éviter les traitements en double grâce à `eventId`.
