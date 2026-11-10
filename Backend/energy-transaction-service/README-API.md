# transaction-service - guide rapide

## Lancer
```bash
docker compose up -d          # PostgreSQL + RabbitMQ
./mvnw spring-boot:run        # http://localhost:8084
./mvnw test
```

## Identite (fournie par l'API Gateway)
Le service fait confiance aux en-tetes ajoutes par la Gateway apres validation du jeton
(il ne doit donc pas etre expose directement) :

| En-tete | Role |
|---|---|
| `X-User-Id` | obligatoire, sinon **401** |
| `X-Household-Id` | foyer de l'utilisateur (filtre de la liste) |
| `X-User-Role` | `ADMIN` ou `USER` |

## Cycle de vie
`PENDING_PAYMENT` -> `PAID` -> `COMPLETED`, annulation possible depuis `PENDING_PAYMENT` / `PAID`.
- Paiement reussi -> evenement **TransactionConfirmed** (routing key `transaction.confirmed`)
- Annulation -> evenement **TransactionCancelled** (`transaction.cancelled`) ; paiement marque `REFUNDED` si deja paye

## Tester avec curl
```bash
# creer (le Marketplace doit repondre a GET {app.marketplace.base-url}/reservations/1)
curl -i -X POST localhost:8084/api/transactions -H 'X-User-Id: 10' -H 'X-Household-Id: 100' \
  -H 'Content-Type: application/json' -d '{"reservationId":1}'

# payer (tok_declined -> 402, tok_unavailable -> 503)
curl -i -X POST localhost:8084/api/transactions/1/payments -H 'X-User-Id: 10' \
  -H 'Content-Type: application/json' -d '{"method":"CARD","cardToken":"tok_ok"}'

# cloturer (vendeur), justificatif, historique
curl -i -X PATCH localhost:8084/api/transactions/1/complete -H 'X-User-Id: 20'
curl -i localhost:8084/api/transactions/1/receipt -H 'X-User-Id: 10'
curl -i localhost:8084/api/transactions/1/history -H 'X-User-Id: 10'

# liste filtree et paginee
curl -i 'localhost:8084/api/transactions?status=PAID&page=0&size=10' -H 'X-User-Id: 10' -H 'X-Household-Id: 100'
```
