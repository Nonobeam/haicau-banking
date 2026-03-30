# haicau

Multi-module haicau workspace (Spring Boot + Maven).

## Architecture

![Overall Architecture](overal-architecture.drawio)

## Ledger design

The platform uses a typed Chart of Accounts (CoA) path format. Every account is identified by a
colon-delimited path such as `wallet:{customer_id}:{wallet_id}:main` or
`receivable:counterparty:stripe`.

**Provider trust model:** The platform trusts the payment provider (e.g. Stripe) to settle.
When a customer initiates a deposit, a receivable claim is recorded immediately against the
provider — the platform accepts the risk that the provider may fail to confirm. The customer
wallet follows `reserved → clearing → main` for internal auditability and clean reversal paths,
independent of the trust decision. The customer's balance is not spendable until the sweep job
moves funds from `clearing` to `main`.

See [plan-coa-redesign.md](plan-coa-redesign.md) for the full CoA design and deposit flow.

## Modules

- `hcau-general-ledger`
  General ledger system server.
- `hcau-banking-reconcile`
  Reconcile worker/service for the general ledger.
- `hcau-banking-common`
  Shared common module for the general ledger.
- `hcau-banking-reconcile-common`
  Shared reconcile scheduler/library module.
- `hcau-banking-module-common`
  Shared module resources/utilities.
- `hcau-platform-common`
  Shared platform module consumed by platform services.
- `hcau-platform-service`
  Platform-facing Spring Boot service.

## Tech stack

- Java 21
- Spring Boot 4.0.0
- Maven
- PostgreSQL
- Flyway (migration plugin in server modules)

## Build

From repository root, build/install modules with Maven in dependency order:

```bash
mvn -f hcau-banking-module-common/pom.xml clean install
mvn -f hcau-banking-common/pom.xml clean install
mvn -f hcau-banking-reconcile-common/pom.xml clean install
mvn -f hcau-platform-common/pom.xml clean install
mvn -f hcau-general-ledger/pom.xml clean install
mvn -f hcau-banking-reconcile/pom.xml clean install
mvn -f hcau-platform-service/pom.xml clean install
```

## Docker Compose

Run from repository root.

### Start infrastructure only

```bash
docker compose -f docker-compose.infra.yml up -d
```

### Start apps (with infrastructure)

```bash
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml --profile apps up -d
```

### Stop and remove containers

```bash
docker compose -f docker-compose.infra.yml -f docker-compose.apps.yml down
```

### Endpoints

- Kafka UI: http://localhost:8090
- General ledger: http://localhost:8081
- Banking reconcile: http://localhost:8082

## Notes

- Environment files are expected per module (`.env`, `.env.example`).
- Flyway helper command examples are documented inside module READMEs.
