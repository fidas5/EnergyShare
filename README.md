# EnergyShare — Smart Solar Energy Sharing Platform

**EnergyShare** is an academic microservices-based platform designed to explore how households in Tunisia could monitor solar electricity production, identify surplus energy, and participate in a **simulated local energy-sharing marketplace**.

The project is motivated by the challenges of growing electricity demand and power interruptions, particularly during periods of high summer consumption. EnergyShare proposes a digital approach to improving the visibility, coordination, and potential use of locally produced renewable energy.

> **Academic prototype:** EnergyShare simulates energy offers, reservations, exchanges, and transactions. It does **not** physically transfer electricity between households or claim to enable energy trading outside applicable Tunisian regulations.

## Project objectives

- Help households visualize solar production, consumption, and estimated surplus.
- Allow eligible households to publish and discover surplus-energy offers.
- Coordinate simulated reservations and exchanges between households.
- Track simulated transactions and their status.
- Provide AI-assisted recommendations to improve energy usage and matching.
- Demonstrate a distributed architecture using independently deployable microservices.

## Microservices

The application is organized around **five business microservices**. Each service owns its domain logic and data; services exchange identifiers and communicate through APIs or events rather than sharing a database.

### 1. Household Management Service

Manages household profiles and their participation in the platform.

**Main responsibilities:**
- Manage household information and participation status.
- Register solar installations and household energy preferences.
- Expose household data needed by other authorized services.

### 2. Energy Monitoring Service

Tracks energy production and consumption information.

**Main responsibilities:**
- Record or simulate solar production and household consumption measurements.
- Calculate estimated energy balances and available surplus.
- Provide monitoring data, history, and dashboard indicators.
- Publish energy-balance updates for other services when appropriate.

### 3. Energy Marketplace Service

Coordinates the marketplace for available surplus energy and household requests.

**Main responsibilities:**
- Create, update, browse, and close energy offers.
- Manage energy requests and reservations.
- Track remaining offered quantities and reservation statuses.
- Check relevant household or surplus information through service-to-service APIs.
- Publish marketplace events for downstream transaction processing.

**Planned stack:** Spring Boot, Spring Data MongoDB, **MongoDB**, OpenFeign, and RabbitMQ.

**Database:** `energy_marketplace_db` (owned exclusively by this microservice).

### 4. Energy Transaction Service

Handles the lifecycle of **simulated** energy transactions.

**Main responsibilities:**
- Record exchanges associated with marketplace reservations.
- Manage transaction confirmation, cancellation, and status tracking.
- Simulate payment or settlement flows where required by the academic scenario.
- React to marketplace events and publish transaction outcomes.

### 5. AI Energy Optimization Service

Provides intelligent decision support for the energy-sharing workflow.

**Main responsibilities:**
- Estimate or forecast energy production and demand using available data.
- Suggest suitable energy offers or matching opportunities.
- Recommend ways to improve surplus usage and household energy planning.
- Expose recommendations to the frontend or other authorized services.

**Planned backend:** Spring Boot. AI techniques and models will be selected during implementation.

### Core technologies

| Layer | Technologies |
| --- | --- |
| Frontend | React, Vite, JavaScript, CSS |
| Backend | Java, Spring Boot, Spring Web |
| API routing | Spring Cloud Gateway |
| Service discovery | Netflix Eureka |
| Centralized configuration | Spring Cloud Config |
| Authentication and authorization | Keycloak |
| Synchronous communication | REST APIs, OpenFeign |
| Asynchronous communication | RabbitMQ |
| Marketplace persistence | MongoDB, Spring Data MongoDB |
| Other service persistence | Separate database per service; engines to be finalized |
| Development tools | Git, GitHub, Maven, npm |

### Architecture principles

- **Database per service:** each microservice owns its data and schema.
- **Loose coupling:** services communicate via REST APIs and asynchronous events.
- **Single frontend entry point:** the React application calls backend APIs through the API Gateway.
- **Independent development:** team members can work on individual microservices within one repository.
- **Security:** Keycloak provides centralized identity and access management.



> Folder names are the proposed repository convention and can be adjusted to match the team's actual modules.

## Getting started

### Prerequisites

- Node.js and npm (compatible with the installed Vite version)
- JDK and Maven versions compatible with the selected Spring Boot / Spring Cloud release train
- MongoDB instance or MongoDB Atlas connection for the Marketplace service
- RabbitMQ, Keycloak, Eureka, and Config Server when the corresponding integrations are enabled

### Run the frontend

```bash
cd Frontend/energyshare-react
npm install
npm run dev
```

Open the local address displayed by Vite (usually `http://localhost:5173`).

## Team collaboration

The project is developed collaboratively, with responsibilities divided by microservice and shared infrastructure. Contributions should be committed to feature branches and reviewed before merging into the main branch.

## Academic scope

EnergyShare is an educational proof of concept. Energy quantities, matching decisions, reservations, and transaction flows may use simulated or sample data. Real-world electricity distribution, grid integration, regulatory compliance, and financial settlement are outside the initial implementation scope.
