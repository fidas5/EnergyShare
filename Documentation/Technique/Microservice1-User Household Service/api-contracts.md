# User & Household Service — REST API Contracts

## Objectif

Le microservice User & Household permet de gérer les profils des utilisateurs, leurs foyers, les membres de chaque foyer, les installations solaires et les préférences énergétiques. Il fournit aux autres microservices (notamment Energy Marketplace) les informations d'un foyer.

**Technologie :** Spring Boot, Spring Data JPA  
**Base de données :** PostgreSQL — `household_db`  
**URL logique via API Gateway :** `/api/users`, `/api/households` et `/api/installations`

## Endpoints REST

Les URI sont relatives à `/api`.

### Utilisateurs

| Méthode | URI | Description | Réponse |
|---|---|---|---|
| POST | `/users` | Créer le profil de l'utilisateur après son inscription | 201, 400, 409 |
| GET | `/users/me` | Consulter son profil | 200, 404 |
| PATCH | `/users/me` | Modifier son profil | 200, 400 |

### Foyers et membres

| Méthode | URI | Description | Réponse |
|---|---|---|---|
| POST | `/households` | Créer un foyer | 201, 400 |
| GET | `/households/{id}` | Consulter un foyer | 200, 403, 404 |
| PATCH | `/households/{id}` | Modifier un foyer | 200, 400, 403, 404 |
| DELETE | `/households/{id}` | Supprimer un foyer | 204, 403, 404, 409 |
| GET | `/households/{id}/members` | Lister les membres d'un foyer | 200, 403, 404 |
| POST | `/households/{id}/members` | Ajouter un membre au foyer | 201, 400, 403, 404, 409 |
| DELETE | `/households/{id}/members/{memberId}` | Retirer un membre du foyer | 204, 403, 404, 409 |

### Installations solaires

| Méthode | URI | Description | Réponse |
|---|---|---|---|
| POST | `/households/{id}/installations` | Déclarer une installation solaire | 201, 400, 403, 404, 409 |
| GET | `/households/{id}/installations` | Lister les installations d'un foyer | 200, 403, 404 |
| PATCH | `/installations/{id}` | Modifier une installation | 200, 400, 403, 404 |
| DELETE | `/installations/{id}` | Supprimer une installation | 204, 403, 404 |

### Préférences énergétiques

| Méthode | URI | Description | Réponse |
|---|---|---|---|
| GET | `/households/{id}/preferences` | Consulter les préférences du foyer | 200, 403, 404 |
| PUT | `/households/{id}/preferences` | Créer ou remplacer les préférences | 200, 400, 403, 404 |

## Exemple — Création d'un foyer

**POST `/api/households`**

Requête JSON :

```json
{
  "name": "Maison Ben Salah",
  "address": "12 rue des Oliviers",
  "city": "Nabeul",
  "householdType": "PROSUMER"
}
```

Réponse — `201 Created` :

```json
{
  "id": 101,
  "name": "Maison Ben Salah",
  "address": "12 rue des Oliviers",
  "city": "Nabeul",
  "householdType": "PROSUMER",
  "createdAt": "2026-11-09"
}
```

Le créateur du foyer est automatiquement enregistré comme membre avec le rôle `OWNER`.

## Exemple — Consultation d'un foyer par Energy Marketplace

**GET `/api/households/101`**

Réponse — `200 OK` :

```json
{
  "id": 101,
  "name": "Maison Ben Salah",
  "city": "Nabeul",
  "householdType": "PROSUMER"
}
```

Energy Marketplace utilise `householdType` pour vérifier que le foyer peut publier une offre.

## Exemple — Déclaration d'une installation solaire

**POST `/api/households/101/installations`**

Requête JSON :

```json
{
  "capacity": 6.5,
  "panelCount": 12,
  "panelType": "Monocristallin",
  "installationDate": "2025-06-15"
}
```

Réponse — `201 Created` :

```json
{
  "id": 7,
  "householdId": 101,
  "capacity": 6.5,
  "panelCount": 12,
  "panelType": "Monocristallin",
  "installationDate": "2025-06-15",
  "status": "ACTIVE"
}
```

Après la création, le service publie un événement pour informer les autres microservices (voir `events-rabbitmq.md`).

## Codes HTTP

- **200 OK :** opération réussie.
- **201 Created :** ressource créée.
- **204 No Content :** suppression effectuée.
- **400 Bad Request :** données invalides.
- **401 Unauthorized :** authentification absente ou invalide.
- **403 Forbidden :** l'utilisateur n'est pas membre du foyer ou n'a pas le rôle nécessaire.
- **404 Not Found :** ressource introuvable.
- **409 Conflict :** conflit métier, par exemple email déjà utilisé, utilisateur déjà membre du foyer ou suppression d'un foyer qui a encore d'autres membres ou installation déclarée par un foyer `CONSUMER`.
- **503 Service Unavailable :** dépendance nécessaire indisponible.

## Sécurité

Les endpoints nécessitent un JWT valide fourni par Keycloak. Le backend vérifie également que l'utilisateur est membre du foyer concerné, grâce à la table `HouseholdMember`. Seul un membre avec le rôle `OWNER` peut modifier ou supprimer le foyer et gérer ses membres.

Les microservices autorisés, comme Energy Marketplace, peuvent consulter les informations de base d'un foyer.

Les identifiants de foyer transmis dans les requêtes ne constituent pas, à eux seuls, une preuve d'autorisation.
