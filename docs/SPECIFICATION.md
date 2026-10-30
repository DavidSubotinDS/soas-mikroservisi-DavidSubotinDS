I need you to build my university project for the subject **Servisno orijentisana arhitektura sistema (SOAS), 2025/26** in this repository.

This repository is the GitHub Classroom repository assigned to me under the `SOAS-2025-26` GitHub organization.

Your task is not merely to give me a plan. Inspect the repository first, preserve anything legitimate that already exists, and then actively scaffold and implement the application described below. Work iteratively, run builds/tests as you go, fix failures, and leave the repository in a coherent runnable state.

## 1. SOURCE OF TRUTH AND GENERAL RULES

The following project specification is the authoritative source of truth.

There is also an official course exercises repository:

`https://github.com/SOAS-2025-26/Vezbe`

Use that repository only as guidance for course-compatible Spring/Spring Cloud patterns, dependencies, Eureka, Gateway, Feign, H2, etc. Do not copy another student's implementation.

Current course exercises use approximately:

- Java 17
- Spring Boot 4.0.5
- Spring Cloud 2025.1.1
- Spring Cloud Netflix Eureka
- Spring Cloud Gateway WebFlux
- Spring Cloud OpenFeign
- Spring Data JPA
- H2

Prefer those course-aligned versions unless the current repository already establishes another compatible version that clearly came from the course template. Keep dependency versions mutually compatible.

For the frontend, use **React + Vite + TypeScript**. A clean component library such as MUI is acceptable. Do not use Angular unless there is already a substantial Angular implementation in this repository.

Do not introduce unnecessary infrastructure. In particular, do not replace the required architecture with JWT authentication, PostgreSQL/MySQL, Kubernetes, RabbitMQ, a configuration server, or unrelated services. The assignment specifically requires Basic Authentication, H2, Eureka, Feign and Docker.

Use Maven for the Java projects.

The resulting repository should preferably be organized as a Maven multi-module project because all backend services and shared libraries belong to this single Classroom repository.

A sensible target layout is:

```text
/
├── pom.xml
├── README.md
├── SPECIFICATION_CHECKLIST.md
├── docker-compose.yaml
├── .env.example
├── naming-server/
├── api-gateway/
├── users-service/
├── currency-exchange/
├── currency-conversion/
├── bank-account/
├── crypto-wallet/
├── crypto-exchange/
├── trade-service/
├── service-library/
├── util/
├── frontend/
└── scripts/
```

If an equivalent valid structure already exists, improve it rather than deleting and recreating it.

The executable backend consists of **nine microservices**:

1. naming-server
2. users-service
3. currency-exchange
4. currency-conversion
5. bank-account
6. crypto-wallet
7. crypto-exchange
8. trade-service
9. api-gateway

`service-library` and `util` are shared Maven library modules rather than independently running microservices.

Every actual microservice must register with Eureka.

Use consistent naming, DTOs, validation and error responses across services.

Use `BigDecimal`, not `double`, for monetary amounts and exchange-rate calculations.

Normalize currency codes to uppercase.

Amounts being converted/traded must be strictly greater than zero.

Never permit balances to become negative.

## 2. REQUIRED TECHNOLOGIES

The assignment explicitly requires:

- Maven
- Java
- Docker
- H2 in-memory databases
- React or Angular for the frontend

Use React as described above.

The project must follow the **database-per-service** design pattern. There must be no shared application database.

At minimum:

- `users-service` owns its own H2 database.
- `bank-account` owns its own H2 database.
- `crypto-wallet` owns its own H2 database.

No service may directly read another service's H2 database or JPA repositories.

Service interaction must happen over HTTP through the defined service APIs.

## 3. ABSOLUTE COMMUNICATION RESTRICTIONS

Inter-service communication must use **Feign clients**.

Do not use `RestTemplate` in the business microservices.

The assignment states that `RestTemplate` is forbidden in microservices, with API Gateway being the only stated exception. Nevertheless, use Feign for normal service-to-service business communication wherever possible.

For API Gateway authentication, follow the architectural pattern used in the official SOAS exercises if a special gateway HTTP mechanism is necessary. Do not introduce RestTemplate into ordinary business services.

Calls from the fiat/crypto exchange services to genuinely external third-party APIs are not inter-service calls. Implement those using an appropriate modern HTTP client, but **not RestTemplate**.

Keep third-party API access isolated behind client/adapter classes so it can be mocked in tests.

## 4. SERVICE LIBRARY — REQUIRED SHARED MODULE

Create a `service-library` Maven module.

The assignment explicitly requires this project to declare shared service contracts, Feign proxies, DTOs and other reusable components.

Put shared API contracts here where appropriate, including:

- DTO classes/records
- request DTOs
- response DTOs
- role enum if safely shareable
- exchange-rate response types
- account/wallet DTOs
- conversion/trade result DTOs
- Feign client interfaces
- service contract interfaces where useful
- common constants used for inter-service communication

Avoid putting actual persistent JPA entities in the shared library. Each database-owning service should own its persistence model.

Define Feign clients for the required service-to-service interactions rather than duplicating HTTP URL construction in business services.

## 5. UTIL — GLOBAL EXCEPTION HANDLING

Create a `util` Maven module.

This module must provide **global exception management for the microservices**.

Implement reusable exception classes and a global `@RestControllerAdvice`/equivalent mechanism that is automatically or explicitly included in applicable microservices.

Handle all realistic business errors, especially:

- resource not found
- duplicate user/email
- attempting to create a second OWNER
- invalid role
- forbidden operation
- invalid amount
- unsupported fiat currency
- unsupported crypto currency
- insufficient balance
- missing bank-account holding
- missing wallet holding
- external exchange API failure
- failed currency conversion
- failed crypto trade
- malformed request
- bean validation errors
- Feign/downstream service failures where appropriate

Every handled error must return an appropriate HTTP status and a clear textual explanation.

Use a predictable JSON error structure, for example:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "Quantity must be greater than zero",
  "path": "/currency-conversion"
}
```

Do not send Java stack traces to the client. The assignment explicitly considers a returned stack trace to be an unhandled exception.

## 6. NAMING SERVER — MANDATORY

Create a Eureka naming server.

Required behavior:

- It is a Eureka Server.
- Every application microservice must register with it.
- Recommended port: **8761**.

Use the appropriate Spring Cloud Netflix Eureka Server dependency.

When running with Docker Compose, configure service discovery using container/service names and the Eureka URL appropriately.

The Eureka dashboard should be accessible during development/defense.

## 7. USERS SERVICE — 5 POINTS

Create `users-service`.

Recommended port:

**8770**

It owns an H2 in-memory database containing users.

Each user must have:

- `id`
- `email`
- `password`
- `role`

Allowed roles are exactly:

- `OWNER`
- `ADMIN`
- `USER`

A user has exactly one role.

There may be **only one OWNER in the entire system**.

Use a unique constraint on user email.

Passwords should be stored using BCrypt or another appropriate Spring Security password encoder rather than plain text, while still supporting the required Basic Authentication workflow.

The service must support creating, reading where required, updating and deleting users.

### Authorization

Follow these rules exactly:

**OWNER**
- can add any allowed user
- can update all users
- can delete all users
- can view/manage users as required

**ADMIN**
- can add users only with role `USER`
- can update users with role `USER`
- must not create or promote someone to ADMIN or OWNER
- must not delete users
- may read user information necessary for the management UI

**USER**
- has no access to the users management service

Do not accidentally give USER access to the users management endpoints.

Create an internal authentication/validation contract required by API Gateway so that Gateway can validate Basic Authentication credentials and obtain the authenticated user's email and role without exposing passwords.

### User lifecycle integration

When a new user with role `USER` is created through the users service:

1. create the user successfully;
2. automatically create a corresponding bank account/default fiat holding with:
   - currency `EUR`
   - amount `0`
3. automatically create a corresponding crypto wallet/default crypto holding with:
   - currency `ETH`
   - amount `0`

The PDF's Crypto Wallet section says "bank account with 0 ETH", which is clearly written inside the crypto-wallet requirement. Interpret this as creating the user's **crypto wallet/ETH holding**, and document that interpretation.

Both automatic actions must happen through service APIs/Feign, never by accessing the other databases.

When a USER is deleted, automatically delete that user's bank-account data and crypto-wallet data by calling the respective services.

Perform dependent cleanup before final user deletion where practical so that a failed downstream operation does not silently leave orphaned state.

Also think carefully about user updates:

- if a USER email changes, bank and wallet ownership must remain consistent;
- if OWNER changes a USER to a non-USER role, the user's bank/wallet must no longer violate the rule that only USER accounts may own them;
- if a non-USER becomes USER, provision the EUR and ETH defaults;
- enforce the single-OWNER invariant during role changes as well as creation.

Keep this logic straightforward and well tested.

## 8. CURRENCY EXCHANGE SERVICE

Create `currency-exchange`.

Recommended port:

**8000**

Purpose:

This microservice communicates with an **external API** to retrieve exchange rates for normal fiat currencies.

It must not own fiat balances and must not perform account mutation.

Provide at least:

```text
GET /currency-exchange?from=X&to=Y
```

Return a useful DTO containing at minimum source currency, destination currency and rate.

For equal currencies return a rate of 1.

### External API

Use a real external fiat exchange-rate API.

A suitable no-key default is the current Frankfurter API:

```text
https://api.frankfurter.dev
```

For example its current v2 API exposes pair rates.

Keep the API base URL configurable.

Do not make tests depend on the live internet. Mock/stub the external client in automated tests.

### Authorization

The specification states that a user with **any role** may access this service.

The frontend requirements additionally say currency-exchange functionality must be available without passing through the Login page.

Therefore expose fiat exchange-rate lookup as a public Gateway route while still implementing Basic Authentication for all protected application functionality.

## 9. CURRENCY CONVERSION SERVICE — 5 POINTS

Create `currency-conversion`.

Recommended port:

**8100**

This is the user-facing endpoint for converting **fiat → fiat**.

The mandatory Gateway request shape is:

```text
localhost:8765/currency-conversion?from=X&to=Y&quantity=Q
```

`Q` is the amount of currency `X` exchanged into currency `Y`.

Implement:

```text
GET /currency-conversion?from=X&to=Y&quantity=Q
```

Even though a state-changing POST might normally be more RESTful, preserve the assignment's required endpoint format.

Behavior:

1. identify the authenticated USER from Gateway-propagated identity;
2. validate `from`, `to` and positive `quantity`;
3. inspect the user's bank balance for currency `from` via the Bank Account Feign client;
4. reject the transaction if funds are insufficient;
5. obtain the fiat exchange rate from `currency-exchange` through Feign;
6. calculate the converted amount accurately with `BigDecimal`;
7. subtract the source amount from the user's source fiat holding;
8. add the converted amount to the target fiat holding, creating that currency holding if necessary;
9. return the user's resulting bank-account state plus a clear transaction-report string.

Example transaction text from the assignment is conceptually:

```text
Uspešno je izvršena razmena EUR: 100 za RSD: 11700
```

The exact wording can be polished, but the result must clearly describe what was exchanged.

### Authorization

This service is **USER ONLY**.

- OWNER must receive 403 / have no access.
- ADMIN must receive 403 / have no access.
- USER is authorized.

Do not follow public student implementations that permit OWNER or ADMIN here; that contradicts the project specification.

## 10. BANK ACCOUNT SERVICE — 5 POINTS

Create `bank-account`.

Recommended port:

**8200**

It owns a separate H2 database.

It stores users' fiat holdings.

The required data contains:

- ID
- user's email
- fiat currency code
- amount of that currency

Bank accounts/holdings are permitted only for users whose role is `USER`.

The owning email must correspond to a USER in `users-service`.

The specification describes the email as unique while also requiring a user to own amounts of multiple fiat currencies. Model this in a consistent way:

- conceptually there is one bank account per user/email;
- its holdings may contain multiple currencies;
- persistence rows may therefore use a unique `(email, currencyCode)` constraint;
- never allow two holdings for the same user's same currency.

Document this interpretation in the README/architecture notes rather than silently ignoring the wording conflict.

When a new USER is added to `users-service`, this service must automatically receive/create:

```text
EUR = 0
```

When that USER is deleted, all bank-account holdings for that email must be deleted automatically.

The service must support the functionality needed to:

- inspect accounts
- add holdings/account data
- update holdings
- delete account data
- obtain a balance
- add money
- deduct money safely

Use internal endpoints/contracts as necessary for the conversion/trade services, but keep them well organized.

### Authorization

Follow the explicit role rules:

**OWNER**
- not authorized to use the bank-account service

**ADMIN**
- can add bank account/holding data
- can update bank account/holding data
- can inspect all bank accounts

**USER**
- can inspect only their own account

The specification requires deletion functionality but does not explicitly grant ADMIN a delete permission in its authorization bullet. Take the conservative approach:

- provide the deletion operation required for lifecycle cleanup as an internal service operation used by `users-service`;
- do not expose an unrestricted user-facing deletion operation that contradicts the stated role permissions unless clearly documented.

Internal mutations required by Currency Conversion and Trade must be service-to-service operations and must not become arbitrary public USER balance-editing endpoints.

## 11. CRYPTO WALLET SERVICE — 5 POINTS

Create `crypto-wallet`.

Recommended port:

**8300**

It owns a separate H2 database.

It stores USER crypto holdings.

Required data:

- ID
- user's email
- crypto currency code
- amount

Only users with role `USER` may have crypto wallets.

The owning email must match a USER from `users-service`.

Use the same logical model as bank accounts:

- one logical wallet per USER email
- multiple crypto holdings
- unique `(email, currencyCode)` persistence combination

When a new USER is created, automatically provision:

```text
ETH = 0
```

When a USER is deleted, delete all wallet holdings associated with that email.

Support:

- viewing wallets
- adding holdings
- updating holdings
- deletion required for lifecycle cleanup
- balance lookup
- safe add
- safe deduct

### Authorization

**OWNER**
- no access

**ADMIN**
- can add
- can update
- can inspect all wallets

**USER**
- can inspect only their own wallet

As with Bank Account, keep lifecycle/internal mutation endpoints distinct from ordinary public management endpoints.

## 12. CRYPTO EXCHANGE SERVICE — 5 POINTS

Create `crypto-exchange`.

Recommended port:

**8400**

It communicates with a real external API to retrieve crypto exchange rates.

The selected provider should support major cryptocurrencies such as at least BTC and ETH and preferably other common assets.

Expose a clean service API such as:

```text
GET /crypto-exchange?from=X&to=Y
```

It must be capable of supporting the rates needed by Trade Service:

- crypto → crypto
- crypto ↔ USD
- crypto ↔ EUR

You may extend the internal service contract to support this while keeping `/crypto-exchange` understandable.

Select a currently functioning public API during implementation and verify its exact endpoint before committing it. Prefer a provider that does not require a secret API key for simple market-rate retrieval if possible. Coinbase's public exchange-rate/spot data or another current public crypto-rate API is acceptable if verified.

Keep the provider adapter isolated and configurable so it can be replaced without rewriting business logic.

Mock the provider in tests.

### Authorization

A user with any application role may access this service.

As with `currency-exchange`, the UI specification says crypto-exchange functionality must be usable without going through Login, so make the simple rate lookup publicly accessible through Gateway.

## 13. TRADE SERVICE — 15 POINTS

Create `trade-service`.

Recommended port:

**8600**

This service implements exchange between fiat and crypto assets.

The mandatory Gateway request form is:

```text
localhost:8765/trade-service?from=X&to=Y&quantity=Q
```

Implement:

```text
GET /trade-service?from=X&to=Y&quantity=Q
```

where Q is the quantity of X exchanged into Y.

This is **USER ONLY**:

- OWNER cannot access it.
- ADMIN cannot access it.
- USER can access it.

Implement all three required transaction categories.

### A. Crypto → Crypto

Allow a USER to exchange crypto they currently own into any other supported cryptocurrency exposed by `crypto-exchange`.

Process:

1. validate source/target assets and quantity;
2. inspect source crypto balance;
3. reject if insufficient;
4. retrieve crypto exchange rate via `crypto-exchange`;
5. deduct source crypto;
6. add target crypto;
7. return resulting wallet state plus transaction report.

### B. Fiat → Crypto

A USER may buy crypto with fiat.

Crypto may be directly purchased only using:

- USD
- EUR

If `from` is USD or EUR:

1. verify the USER has sufficient bank balance;
2. obtain the fiat/crypto market rate through the appropriate exchange-service contract;
3. deduct the required fiat;
4. add the resulting crypto to the wallet.

If `from` is some other fiat currency, for example RSD:

1. convert the provided fiat into either USD or EUR through `currency-conversion`;
2. `currency-conversion` must perform the corresponding mutation of the bank account;
3. use that intermediate USD/EUR holding to purchase the crypto;
4. deduct the intermediate USD/EUR amount;
5. add the resulting crypto to the wallet.

Do not leave an incorrect duplicate intermediate balance.

### C. Crypto → Fiat

A USER may sell crypto into fiat.

Crypto can be directly exchanged only to:

- USD
- EUR

If `to` is USD or EUR:

1. validate crypto balance;
2. obtain exchange rate;
3. deduct crypto;
4. add resulting USD/EUR to bank account.

If the requested destination is another fiat, for example RSD:

1. convert the crypto to USD or EUR;
2. add the intermediate USD/EUR to the bank account;
3. call `currency-conversion` to convert the intermediate currency into the desired fiat;
4. the conversion must deduct the intermediate USD/EUR and add the final fiat.

### Successful response

When crypto is converted into fiat, return:

- resulting bank-account state
- transaction-report string

When fiat is converted into crypto or crypto is converted into crypto, return:

- resulting crypto-wallet state
- transaction-report string

Make the response DTO explicit and easy for the React frontend to consume.

Prevent partial mutations as far as reasonably possible. Validate funds and rates before mutation. Where a downstream mutation can fail after a previous one succeeded, either implement a simple compensation operation or structure operations carefully so errors do not silently destroy funds.

Do not attempt to implement a distributed transaction manager; keep the solution understandable for a university project.

## 14. REQUIRED FAULT TOLERANCE — 5 POINTS

The Trade microservice must implement a fault-tolerance mechanism.

The specification accepts:

- circuit breaker
OR
- retry

Prefer a **Resilience4j circuit breaker** around the external/rate dependency if it is compatible with the course Spring Boot version.

If the exact Resilience4j integration demonstrated in the official course repository is more appropriate, follow that course pattern.

If a circuit breaker integration is incompatible with Spring Boot 4, a clearly implemented and tested retry mechanism is acceptable because the specification explicitly allows retry.

A downstream/external outage must produce a clean service-unavailable/business response, not a stack trace.

Add tests that demonstrate the fallback/failure behavior.

## 15. API GATEWAY — MANDATORY

Create `api-gateway`.

This is the single entry point for user requests.

The port is mandatory:

**8765**

Use Spring Cloud Gateway, consistent with the official course exercises.

Configure discovery-based routes for all business services.

Typical Gateway route prefixes should include:

```text
/users/**
/bank-accounts/**
/crypto-wallets/**
/currency-exchange/**
/currency-conversion/**
/crypto-exchange/**
/trade-service/**
```

The mandatory user-facing request formats must work exactly:

```text
http://localhost:8765/currency-conversion?from=X&to=Y&quantity=Q

http://localhost:8765/trade-service?from=X&to=Y&quantity=Q
```

### Basic Authentication

**Basic Authentication MUST be implemented at API Gateway.**

This is non-negotiable. The assignment explicitly states that if Basic Authentication is not implemented at Gateway level, the microservice/project portion is considered not completed.

Do not replace Basic Auth with JWT.

Gateway must:

1. read Basic Authorization credentials;
2. validate them against `users-service`;
3. return `401 Unauthorized` for invalid/missing credentials on protected routes;
4. obtain the authenticated user's email and role;
5. pass trusted identity information downstream;
6. allow downstream services to enforce role authorization;
7. return `403 Forbidden` when an authenticated role is not permitted.

An endpoint such as:

```text
GET /auth/me
```

implemented at Gateway level is acceptable for the React login page to validate credentials and obtain the current user's email/role.

The rate lookup routes:

```text
/currency-exchange
/crypto-exchange
```

should remain public because the UI specification explicitly requires those two functionalities to be usable outside the Login page.

All other user-facing application functionality should require authentication.

Configure CORS for the React development origin, normally:

```text
http://localhost:5173
```

## 16. AUTHORIZATION MATRIX — DO NOT DEVIATE

Implement and test this effective matrix:

| Functionality | OWNER | ADMIN | USER | Unauthenticated |
|---|---|---|---|---|
| Fiat exchange-rate lookup | Yes | Yes | Yes | Yes |
| Crypto exchange-rate lookup | Yes | Yes | Yes | Yes |
| Users management | Full CRUD | Create/update USER + permitted viewing | No | No |
| Bank accounts | No | Add/update/view all | View own only | No |
| Crypto wallets | No | Add/update/view all | View own only | No |
| Fiat currency conversion | No | No | Yes | No |
| Trade service | No | No | Yes | No |

Internal service-to-service lifecycle/mutation APIs are separate from this public authorization matrix.

Add backend tests for the important forbidden cases, especially:

- OWNER cannot convert fiat.
- ADMIN cannot convert fiat.
- OWNER cannot trade.
- ADMIN cannot trade.
- USER cannot manage users.
- OWNER cannot use bank-account management.
- OWNER cannot use crypto-wallet management.
- USER cannot inspect another USER's balances.
- ADMIN cannot create ADMIN/OWNER users.
- ADMIN cannot delete users.

## 17. FRONTEND — 30 POINTS

Create a functional React frontend that exposes the functionality of every created business service except:

- naming-server
- api-gateway

Those two infrastructure services do not require frontend pages.

The UI must present functionality for:

- Users Service
- Currency Exchange
- Currency Conversion
- Bank Account
- Crypto Wallet
- Crypto Exchange
- Trade Service

### Public functionality

These pages must work without login:

- Fiat exchange-rate lookup
- Crypto exchange-rate lookup

### Login-protected functionality

All other functionality must be behind a Login page.

The application must use API Gateway as its backend base URL:

```text
http://localhost:8765
```

Do not call individual business services directly from React.

Because authentication is Basic Auth, the frontend may retain the Basic Authorization value for the current browser session, preferably in memory/session storage rather than persistent local storage.

Provide Logout which clears credentials.

After login, retrieve the user's role and render the appropriate navigation/actions.

### OWNER UI

Show:

- user management
- create users
- edit users
- delete users

Do not show:

- bank management
- wallet management
- currency conversion
- crypto trading

The public exchange-rate pages remain accessible.

### ADMIN UI

Show:

- user management relevant to USER accounts
- create USER
- update USER
- bank-account management
- crypto-wallet management

Do not permit:

- creating ADMIN
- creating OWNER
- deleting users
- currency conversion
- trade

### USER UI

Show:

- own bank account and fiat balances
- own crypto wallet and balances
- fiat currency conversion
- trade page for:
  - crypto → crypto
  - fiat → crypto
  - crypto → fiat

Do not show Users management.

### UX expectations

Use a clear layout with:

- responsive navigation
- login page
- role-aware navigation
- loading indicators
- empty states
- clear success/error notifications
- confirmation before destructive management operations
- currency selectors
- numeric amount validation
- formatted balances
- transaction-result display

The application does not have to be visually extravagant, but it should look deliberate and be easy to demonstrate.

Do not rely only on hidden frontend controls for authorization. Backend authorization remains mandatory.

## 18. H2 DATABASES

Use H2 in-memory databases as required.

Each database-owning microservice must have its own independent H2 configuration.

Use separate logical database names, for example:

```text
usersdb
bankdb
walletdb
```

Do not share one H2 URL.

Enable the H2 console only if useful for development/defense and do not make it part of the application's functional API.

Use JPA constraints and service validation to preserve data integrity.

## 19. DEMO DATA

Create deterministic demo data so the application can be demonstrated immediately.

At minimum provide:

- exactly one OWNER
- at least one ADMIN
- at least one USER

Use obvious academic/demo credentials, not secrets.

For example:

```text
owner@soas.local / Owner123!
admin@soas.local / Admin123!
user@soas.local  / User123!
```

You may choose equivalent credentials, but they must be documented in README.

There must never be more than one OWNER.

Give the demo USER sensible balances so conversion/trade can actually be demonstrated, while still ensuring the automatic USER-creation behavior initializes a newly created USER with:

```text
EUR = 0
ETH = 0
```

Do not confuse seeded demo balances with the default behavior for newly created users.

## 20. EXTERNAL API DESIGN

Keep both rate-provider implementations configurable and testable.

### Fiat

Prefer a current no-key service such as Frankfurter:

```text
https://api.frankfurter.dev
```

Verify the exact endpoint when implementing.

### Crypto

Select and verify a currently available API supporting the largest crypto assets and the USD/EUR quotes required by Trade.

A public provider with no required key is preferable for a university defense because the application should run without secret setup.

Do not put API secrets in Git.

If an API key becomes unavoidable:

- read it from an environment variable;
- put only the variable name in `.env.example`;
- never commit the real secret;
- fail with a clear configuration error.

Automated tests must mock the external providers rather than consume live API quotas.

## 21. DOCKER — 15 POINTS

The assignment requires Docker images for all submitted microservices.

Create Docker images for all nine executable backend services:

- naming-server
- api-gateway
- users-service
- currency-exchange
- currency-conversion
- bank-account
- crypto-wallet
- crypto-exchange
- trade-service

`service-library` and `util` are build dependencies, not standalone containers.

The frontend explicitly does **not** need to be Dockerized.

Create suitable Dockerfiles/multi-stage builds.

Each final service container should run the packaged Spring Boot JAR.

Create a root:

```text
docker-compose.yaml
```

that launches the complete backend.

Use Eureka-aware container configuration.

Expose at minimum:

- `8761` for Eureka
- `8765` for API Gateway

Individual business services should still listen on their required/recommended internal ports.

Publishing all business ports to the host is optional; Gateway should remain the normal public entry point.

Use health checks/dependency ordering where practical, but do not depend solely on `depends_on` for application readiness.

### Docker Hub

The specification requires all produced microservice images to be placed in repositories belonging to my own Docker Hub account with tag:

```text
latest
```

Prepare the repository for this even if credentials are not available to you.

Use an environment variable such as:

```text
DOCKERHUB_USERNAME
```

and image names such as:

```text
${DOCKERHUB_USERNAME}/soas-naming-server:latest
${DOCKERHUB_USERNAME}/soas-api-gateway:latest
${DOCKERHUB_USERNAME}/soas-users-service:latest
${DOCKERHUB_USERNAME}/soas-currency-exchange:latest
${DOCKERHUB_USERNAME}/soas-currency-conversion:latest
${DOCKERHUB_USERNAME}/soas-bank-account:latest
${DOCKERHUB_USERNAME}/soas-crypto-wallet:latest
${DOCKERHUB_USERNAME}/soas-crypto-exchange:latest
${DOCKERHUB_USERNAME}/soas-trade-service:latest
```

Allow Compose to build locally as well as use these image names.

Document commands such as:

```bash
docker compose build
docker compose up -d
docker compose push
```

Do not claim images have been pushed if Docker Hub credentials are not actually available.

## 22. TESTING REQUIREMENTS

Create meaningful automated tests.

Do not settle for tests that merely start the Spring context.

At minimum cover:

### Users

- create USER
- create ADMIN as OWNER
- reject second OWNER
- reject ADMIN creating ADMIN
- reject ADMIN creating OWNER
- reject USER access
- USER creation triggers bank/wallet provisioning
- USER deletion triggers bank/wallet cleanup
- role changes preserve lifecycle rules

### Bank Account

- new USER default EUR holding
- amount cannot become negative
- ADMIN permissions
- USER own-account access
- USER cannot access someone else's account
- OWNER forbidden
- add/update/balance operations

### Crypto Wallet

- new USER default ETH holding
- ADMIN permissions
- USER own-wallet access
- USER cannot access another wallet
- OWNER forbidden
- add/update/balance operations

### Currency Exchange

- successful mocked fiat rate
- equal currency = 1
- unsupported currency
- external API failure

### Currency Conversion

- correct rate calculation
- successful balance mutation
- target holding creation
- insufficient funds
- invalid quantity
- USER allowed
- ADMIN forbidden
- OWNER forbidden

### Crypto Exchange

- successful mocked crypto rate
- major crypto support
- invalid asset
- provider failure

### Trade

- crypto → crypto
- EUR → crypto
- USD → crypto
- non-USD/EUR fiat → USD/EUR → crypto
- crypto → EUR
- crypto → USD
- crypto → USD/EUR → other fiat
- insufficient bank balance
- insufficient crypto balance
- invalid quantity
- ADMIN forbidden
- OWNER forbidden
- USER allowed
- circuit-breaker/retry failure behavior

### Gateway

- public currency-exchange route
- public crypto-exchange route
- protected route without Basic Auth returns 401
- invalid Basic Auth returns 401
- authenticated but unauthorized role returns 403
- valid identity information reaches downstream services
- required conversion route works
- required trade route works

Where external HTTP APIs are involved, use mocks/stubs.

Run the complete backend test suite from the root with Maven.

The desired final backend verification should include at least:

```bash
mvn clean test
mvn clean package
```

For React:

```bash
npm install
npm run build
```

Add frontend tests where useful, especially auth/role routing and API error handling.

## 23. README — MANDATORY SUBMISSION CONTENT

The GitHub repository must contain an `.md` file listing:

1. **all functional URLs through API Gateway for all microservices**
2. **credentials for all predefined/demo users in the system**

Use `README.md` for this and make it comprehensive.

README should contain:

- project overview
- architecture summary
- technology stack
- repository/module structure
- service table
- service ports
- role/authorization matrix
- all Gateway URLs
- example request bodies/query parameters
- demo credentials
- external API providers
- local Maven startup instructions
- React startup instructions
- Docker Compose instructions
- Docker Hub build/tag/push instructions
- test instructions
- important design assumptions
- troubleshooting

Every user-facing backend URL shown to the evaluator should use:

```text
http://localhost:8765
```

where applicable.

Explicitly include:

```text
GET http://localhost:8765/currency-conversion?from=EUR&to=RSD&quantity=100
GET http://localhost:8765/trade-service?from=EUR&to=BTC&quantity=100
```

plus the complete users/bank/wallet/exchange URLs you implement.

## 24. SPECIFICATION CHECKLIST

Create:

```text
SPECIFICATION_CHECKLIST.md
```

Map every requirement in this prompt to:

- implementation location
- test location
- status

Include the point values:

- Users Service — 5
- Currency Conversion — 5
- Bank Account — 5
- Crypto Wallet — 5
- Crypto Exchange — 5
- Trade Service — 15
- Service Library + Util — 10
- Docker — 15
- User Interface — 30
- Additional specification/fault tolerance — 5

Total: 100 points.

Also clearly identify Naming Server and API Gateway as mandatory requirements.

This checklist is for our own verification and defense preparation.

## 25. SUBMISSION/COURSE REQUIREMENTS TO DOCUMENT

The complete project code must ultimately be submitted in the GitHub repository assigned through:

```text
https://github.com/SOAS-2025-26
```

The project defense can be registered in any exam period, but registration must occur at least **7 days before the exam date**.

The specification gives the contact address for project-defense registration/questions as:

```text
vfficial98@gmail.com
```

The minimum score required to defend/pass the project is:

```text
51 points
```

These are administrative requirements, not application functionality, but record them in the project checklist/README so they are not forgotten.

## 26. IMPORTANT AMBIGUITIES — HANDLE THEM EXPLICITLY

Do not silently invent behavior when the specification has inconsistent wording.

Use these interpretations and document them:

### Crypto default

The Crypto Wallet section says a new USER gets a "bank account" with `ETH = 0`. Because this is inside the Crypto Wallet requirement, treat this as:

```text
new USER → crypto wallet ETH = 0
```

### Email uniqueness in bank/wallet tables

The text describes a unique email but also requires multiple currency amounts.

Interpret it as:

```text
one logical account/wallet owner per email
multiple holdings per owner
unique(email, currencyCode)
```

### Account/wallet deletion permission

The services are required to support deletion, while the explicit ADMIN authorization list only states add/update/view.

Therefore implement deletion at least as a protected internal lifecycle capability used when `users-service` deletes a USER. Do not casually grant a public role capability that contradicts the explicit authorization list.

### Public rate pages vs Basic Authentication

Gateway Basic Authentication is mandatory, but the UI specification explicitly says Currency Exchange and Crypto Exchange are available outside Login.

Therefore:

```text
currency-exchange = public
crypto-exchange   = public
all other user-facing functionality = authenticated
```

Gateway must still contain a complete Basic Authentication implementation.

## 27. CODE QUALITY RULES

Keep the implementation understandable enough for me to explain at an oral/project defense.

Prefer straightforward architecture over enterprise overengineering.

Use:

- constructor injection
- DTO validation
- meaningful method/class names
- service/repository/controller separation
- small focused methods
- reusable Feign contracts
- centralized error handling
- comments only where behavior is non-obvious

Avoid:

- huge god classes
- duplicated authorization logic everywhere
- hardcoded hostnames where service discovery/configuration should be used
- committed secrets
- floating-point money arithmetic
- returning entities directly when DTOs are appropriate
- empty catch blocks
- stack traces in client responses

Create `.gitignore` entries for:

- Maven targets
- node_modules
- IDE-specific files where appropriate
- environment files/secrets
- frontend build output

Do not commit `.env`.

## 28. OPTIONAL BUT USEFUL DEVELOPMENT SUPPORT

If it does not distract from mandatory functionality, add:

```text
scripts/smoke-test.ps1
scripts/smoke-test.sh
```

to exercise the principal Gateway endpoints.

Also consider:

```text
docs/ARCHITECTURE.md
```

with a Mermaid diagram showing:

```text
React
  ↓
API Gateway :8765
  ↓
Eureka discovery
  ├── Users :8770 → H2 users DB
  ├── Currency Exchange :8000 → external fiat API
  ├── Currency Conversion :8100
  │       ├── Currency Exchange
  │       └── Bank Account
  ├── Bank Account :8200 → H2 bank DB
  ├── Crypto Wallet :8300 → H2 wallet DB
  ├── Crypto Exchange :8400 → external crypto API
  └── Trade :8600
          ├── Crypto Exchange
          ├── Crypto Wallet
          ├── Bank Account
          └── Currency Conversion
```

All backend services register with:

```text
Naming Server / Eureka :8761
```

These documentation additions are secondary to getting the required project working.

## 29. IMPLEMENTATION ORDER

Work in an order that keeps the repository buildable.

A good sequence is:

**Foundation**
- inspect repository
- root Maven setup
- service-library
- util
- naming-server
- shared configuration

**Identity**
- users-service
- Basic Auth validation contract
- user tests

**Accounts**
- bank-account
- crypto-wallet
- lifecycle integration
- tests

**Rates**
- currency-exchange
- crypto-exchange
- external API adapters
- tests

**Transactions**
- currency-conversion
- trade-service
- fault tolerance
- tests

**Gateway**
- routes
- Basic Authentication
- identity propagation
- authorization
- CORS
- tests

**Frontend**
- public exchange pages
- login
- role-aware application
- management pages
- USER conversion/trade
- error/loading UX

**Deployment**
- Dockerfiles
- docker-compose.yaml
- environment templates
- health/smoke checks

**Submission quality**
- README
- SPECIFICATION_CHECKLIST.md
- architecture documentation
- final test/build pass

Do not postpone tests until the very end.

## 30. DEFINITION OF DONE

Do not call this project complete merely because all folders/classes exist.

The implementation is complete only when, as far as the available environment permits:

- all Maven modules compile;
- automated backend tests pass;
- React production build passes;
- Eureka server starts;
- all backend microservices register with Eureka;
- API Gateway starts on 8765;
- Basic Authentication works;
- role authorization matches the assignment;
- public fiat and crypto rate lookup works;
- USER creation auto-provisions EUR and ETH;
- USER deletion cleans up bank/wallet state;
- fiat conversion mutates balances correctly;
- all three trade modes work;
- insufficient funds produce clean errors;
- Trade has circuit breaker or retry;
- no business microservice uses RestTemplate;
- inter-service calls use Feign;
- separate H2 databases are used;
- Docker images can be built;
- Docker Compose can start the backend;
- README contains all Gateway URLs and credentials;
- no client response contains an unhandled stack trace.

If Docker is available in your execution environment, perform an actual Compose smoke test.

If Docker is unavailable, validate what you can and explicitly report that runtime Compose verification was not possible. Do not claim success without running it.

## 31. WHAT I WANT YOU TO DO NOW

Start by inspecting the repository with commands such as:

```bash
git status
find/dir appropriate to the current OS
```

and inspect any existing Maven/React configuration.

Then implement the project rather than only describing it.

Before making major structural changes, understand what is already present.

After each major stage:

1. build/test it;
2. fix failures;
3. continue only once the repository is coherent.

You may create all necessary source/configuration/test/documentation files.

Do not push secrets.

Do not force-push or rewrite Git history.

Do not copy code from other students' Classroom repositories.

At the end of this work, give me a concise report containing:

- what was implemented;
- repository structure;
- tests/build commands that actually passed;
- Docker verification actually performed;
- external API selected for fiat;
- external API selected for crypto;
- any specification assumptions made;
- any remaining manual actions that require my credentials, especially Docker Hub login/push;
- any remaining incomplete requirement, clearly identified rather than hidden.

Begin now by inspecting the repository and then proceed with the implementation.