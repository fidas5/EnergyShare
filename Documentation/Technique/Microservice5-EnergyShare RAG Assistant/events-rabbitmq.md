# Microservice 5 — EnergyShare RAG Assistant
## Événements RabbitMQ et intégration interservices

> **Décision d'architecture actuelle :** le microservice 5 ne dépend **pas de RabbitMQ** pour ses fonctionnalités principales. Les échanges du chatbot avec le frontend transitent par **HTTP REST** via Spring Cloud Gateway. Ce document distingue l'architecture retenue des évolutions possibles, pour éviter de présenter des échanges imaginaires comme implémentés.

## 1. Communications effectivement prévues

| Origine | Destination | Mode | Objet |
|---|---|---|---|
| React | API Gateway | HTTPS/REST | Envoi des questions et consultation de l'historique |
| API Gateway | Flask RAG Assistant | HTTP REST interne | Routage vers le service Python |
| Flask | MySQL | Accès DB propre au microservice | Conversations et messages |
| Flask / LangChain | ChromaDB | API locale/cliente | Recherche vectorielle des documents |
| Flask / LangChain | Ollama + Llama 3 | API LLM | Génération des réponses |
| Administration | Flask RAG Assistant | REST protégé | Import de documents |

Le microservice 5 ne consomme pas directement les événements `ReservationCreated`, `TransactionConfirmed` ou `TransactionCancelled` du Marketplace / Transaction dans le périmètre retenu. Il n'intervient pas dans l'autorisation d'une réservation, dans les paiements ou dans le calcul d'un surplus.

## 2. Pourquoi RabbitMQ n'est pas nécessaire ici

Le RAG est déclenché par une **question utilisateur** : le client attend une réponse, et l'interaction convient à une API REST synchrone. La recherche ChromaDB et la génération LLM se déroulent à l'intérieur du traitement de la requête. RabbitMQ est utilisé ailleurs dans EnergyShare pour découpler les événements métier liés aux réservations et aux transactions.

