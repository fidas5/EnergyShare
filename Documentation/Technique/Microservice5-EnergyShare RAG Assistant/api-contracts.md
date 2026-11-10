# Microservice 5 — EnergyShare RAG Assistant
## Contrats API REST (conception proposée)

> **Statut :** contrat de conception pour EnergyShare, à valider avec les routes Flask effectivement implémentées.  
> **Stack :** Python, Flask, LangChain, Hugging Face Embeddings, ChromaDB, MySQL et Ollama/Llama 3 (selon environnement).  
> **Rôle :** assistant documentaire RAG sur l'énergie solaire et le fonctionnement d'EnergyShare. Il ne calcule **pas** les surplus, ne prédit pas la consommation et n'exécute aucune transaction.

## 1. Vue d'ensemble

- Accès utilisateur : **React → Spring Cloud Gateway → API Flask** (route du service Flask configurée explicitement).
- Authentification : identité centralisée dans **Keycloak**. Le Gateway et le service protègent les routes, et le service vérifie l'appartenance des conversations.
- Format : `application/json`, sauf import documentaire multipart.
- Préfixe proposé : `/api/ai`.
- Les IDs de conversation sont des identifiants techniques générés par le serveur ; `ownerUserId` est déterminé à partir de l'identité authentifiée, jamais choisi librement par le client.

## 2. Endpoints

| Méthode | Endpoint | Fonction | Statut succès | Accès |
|---|---|---|---|---|
| `POST` | `/api/ai/chat` | Envoyer une question, poursuivre ou créer une conversation, récupérer une réponse avec références | `200 OK` | Utilisateur connecté |
| `GET` | `/api/ai/conversations` | Lister ses conversations | `200 OK` | Utilisateur connecté |
| `GET` | `/api/ai/conversations/{id}` | Consulter l'historique d'une conversation personnelle | `200 OK` | Propriétaire |
| `DELETE` | `/api/ai/conversations/{id}` | Supprimer une conversation et ses messages | `204 No Content` | Propriétaire |
| `POST` | `/api/ai/documents` | Importer et indexer un document de connaissance | `201 Created` | Administrateur |

**Note :** ces URI, les statuts et les exemples sont des **propositions**, non une déclaration de fonctionnalités déjà développées. Pour `POST /api/ai/chat`, `200` est utilisé même lorsque la conversation est créée ; le contrat pourra évoluer vers `201` en cas de séparation des actions de création.

## 3. POST `/api/ai/chat`

**Requête — nouvelle conversation**

```http
POST /api/ai/chat
Authorization: Bearer <access_token>
Content-Type: application/json
```

```json
{
  "message": "Comment améliorer mon autoconsommation solaire ?"
}
```

**Requête — suite d'une conversation**

```json
{
  "conversationId": "conv_85c2",
  "message": "Et pendant les heures de pointe ?"
}
```

**Réponse `200 OK` (exemple indicatif)**

```json
{
  "conversationId": "conv_85c2",
  "messageId": "msg_a921",
  "answer": "Décaler certains usages vers les heures de production solaire peut améliorer l'autoconsommation, selon votre installation.",
  "sources": [
    {
      "documentId": "doc_guide_solaire",
      "title": "Guide de bonnes pratiques solaires",
      "section": "Autoconsommation"
    }
  ],
  "createdAt": "2026-11-10T14:00:00Z"
}
```

Les sources sont des **références documentaires** issues des métadonnées ChromaDB. Elles ne garantissent pas à elles seules que la réponse est correcte : le service doit essayer de répondre seulement lorsque le contexte retrouvé est suffisant et signaler ses limites sinon.

**Règles de validation :** `message` obligatoire, non vide, longueur maximale configurable ; `conversationId` facultatif et appartenant à l'appelant ; taille d'historique limitée ; vérification de l'authentification côté serveur.

## 4. GET `/api/ai/conversations`

**Réponse `200 OK`**

```json
{
  "items": [
    {
      "id": "conv_85c2",
      "title": "Conseils d'autoconsommation",
      "createdAt": "2026-11-10T13:56:00Z",
      "updatedAt": "2026-11-10T14:00:00Z"
    }
  ]
}
```

Une pagination (`page`, `size`) peut être ajoutée. Ne jamais retourner les conversations d'autres comptes.

## 5. GET `/api/ai/conversations/{id}`

**Réponse `200 OK`**

```json
{
  "id": "conv_85c2",
  "title": "Conseils d'autoconsommation",
  "messages": [
    {
      "id": "msg_001",
      "role": "user",
      "content": "Comment améliorer mon autoconsommation solaire ?",
      "createdAt": "2026-11-10T13:56:00Z"
    },
    {
      "id": "msg_a921",
      "role": "assistant",
      "content": "Décaler certains usages vers les heures de production solaire peut aider.",
      "createdAt": "2026-11-10T14:00:00Z"
    }
  ]
}
```

**Sécurité :** vérification systématique du propriétaire `ownerUserId`. En l'absence de droit, renvoyer `403` ou `404` selon la politique de non-divulgation choisie et documentée.

## 6. DELETE `/api/ai/conversations/{id}`

- `204 No Content` : conversation supprimée ; corps vide.
- La suppression couvre la conversation et ses messages, en une opération cohérente ou via cascade relationnelle.
- `404 Not Found` : conversation inconnue ou masquée par la politique de confidentialité.

## 7. POST `/api/ai/documents` — Administration

**Type :** `multipart/form-data`. Champ attendu : `file`. Métadonnées additionnelles optionnelles : `title`, `category`.

```http
POST /api/ai/documents
Authorization: Bearer <admin_access_token>
Content-Type: multipart/form-data
```

**Réponse `201 Created` (si indexation synchrone)**

```json
{
  "documentId": "doc_9b03",
  "title": "FAQ EnergyShare",
  "status": "INDEXED",
  "chunksIndexed": 24
}
```

Si l'indexation devient asynchrone, préférer `202 Accepted` et exposer un suivi de tâche. Contrôler les extensions et formats acceptés, la taille, les permissions, les données personnelles, et rejeter les documents non fiables. `TextLoader` vise notamment des fichiers texte ; les PDF nécessitent un chargeur adapté.

## 8. Codes HTTP et erreurs

| Code | Cas typique |
|---|---|
| `200` | Lecture ou réponse de chat réussie |
| `201` | Document créé et indexé immédiatement |
| `204` | Suppression réussie |
| `400` | Champ manquant, requête mal formée, type de fichier rejeté |
| `401` | Jeton absent, invalide ou expiré |
| `403` | Droits insuffisants ou accès refusé |
| `404` | Conversation ou document introuvable |
| `413` | Fichier trop volumineux |
| `429` | Trop de requêtes |
| `500` | Erreur serveur inattendue |
| `503` | LLM ou base vectorielle indisponible |

**Format d'erreur proposé :**

```json
{
  "code": "RAG_BACKEND_UNAVAILABLE",
  "message": "L'assistant est temporairement indisponible.",
  "requestId": "req_c19e"
}
```

Ne pas exposer les prompts internes, secrets, traces d'erreurs ou documents privés dans les réponses HTTP.

## 9. Tests contractuels minimaux

1. Une question retourne `conversationId`, `answer` et `sources` lorsque des sources pertinentes existent.
2. Deux utilisateurs ne peuvent pas lire ni supprimer leurs conversations respectives.
3. Une question vide est rejetée en `400`.
4. Un compte non administrateur ne peut pas importer de document (`403`).
5. Si le LLM est indisponible, le service retourne une erreur contrôlée (`503`) ou un message de repli selon le contrat retenu.
6. Les sources citent des documents indexés ; aucune source n'est inventée.

## 10. Dépendances documentaires

Voir aussi [`mysql-chromadb-business-design.md`](mysql-chromadb-business-design.md), [`rag-architecture.md`](rag-architecture.md) et [`events-rabbitmq.md`](events-rabbitmq.md).
