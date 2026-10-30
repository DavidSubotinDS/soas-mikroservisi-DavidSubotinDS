# SOAS Exchange Desk

University project for **Servisno orijentisana arhitektura sistema, 2025/26**. Nine Spring microservices provide user management, separate fiat and crypto holdings, public rates, fiat conversion and crypto trading. React communicates exclusively with API Gateway on **8765**.

This repository was implemented locally from an empty Git repository. No university remote is configured and nothing has been pushed. The university repository must be assigned separately. The original supplied requirements are preserved in [docs/SPECIFICATION.md](docs/SPECIFICATION.md); the user's instruction to work locally overrides its initial Classroom-repository assumption.

## Quick start

Requirements: **JDK 17**, Maven 3.9+, Node 22.12+ and npm; Docker Desktop with Linux containers for Compose. Run commands from the repository root.

### Docker Compose

```powershell
Copy-Item .env.example .env
# Set DOCKERHUB_USERNAME in .env to your own Docker Hub username.
docker compose config --quiet
docker compose build
docker compose up -d --wait --wait-timeout 240
```

Open [the frontend](http://localhost:5173), [Gateway health](http://localhost:8765/actuator/health), and [Eureka](http://localhost:8761). New containers can take 60–120 seconds to register and provision the demo USER. Business-service ports are private to the Compose network. All application databases are in memory: restarting their containers resets data.

```powershell
docker compose logs -f users-service api-gateway
docker compose down
```

### Maven and React, without Docker

On this Windows machine `java` may point to Java 8. Use the installed JDK 17:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.15.6-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean test
mvn clean package
.\scripts\start-local.ps1
cd frontend
npm install
npm run dev
```

On Linux/macOS set `JAVA_HOME` to JDK 17, run `mvn clean package`, then `bash scripts/start-local.sh`. Start React with `cd frontend && npm install && npm run dev`. Maven uses the ignored, workspace-local `.tools/m2` cache configured in `.mvn/maven.config`.

The startup scripts launch all nine packaged services and record only their own process IDs in `.runtime/`. Stop them with `scripts/stop-local.ps1` or `bash scripts/stop-local.sh`. Stop Vite with Ctrl+C. Alternatively run each `<module>/target/<module>-1.0.0.jar` with `java -jar`, starting Eureka first. Logs are in `.runtime/`.

## Demo credentials

These are intentionally public academic demo credentials, seeded when `soas.demo-data=true` (the default).

| Email | Password | Role | Initial holdings |
|---|---|---|---|
| owner@soas.local | Owner123! | OWNER | None |
| admin@soas.local | Admin123! | ADMIN | None |
| user@soas.local | User123! | USER | EUR 10000, ETH 2 |

The demo USER is provisioned asynchronously after the bank and wallet services become discoverable. **Every newly created USER starts with EUR 0 and ETH 0.** Demo funding is a separate idempotent operation. Passwords in the users database are BCrypt hashes. Set `SOAS_DEMO_DATA=false` / `--soas.demo-data=false` to disable demo seeding; for this academic project retain it for a usable initial OWNER.

## Architecture and modules

Java **17**, Spring Boot **4.0.5**, Spring Cloud **2025.1.1**, Maven, Eureka, Gateway WebFlux, OpenFeign, Spring Data JPA, H2, Spring Security Basic Auth, React 19, Vite 7, TypeScript, Vitest and Docker Compose.

Versions and architectural patterns were checked against the [official SOAS exercises](https://github.com/SOAS-2025-26/Vezbe). This is an independent implementation. See [architecture and compensation design](docs/ARCHITECTURE.md) and [the specification checklist](SPECIFICATION_CHECKLIST.md).

| Module | Port | Responsibility / database |
|---|---:|---|
| naming-server | 8761 | Mandatory Eureka server; also registers itself |
| api-gateway | 8765 | Mandatory single entry point; Basic Auth, CORS, role rules, identity forwarding |
| users-service | 8770 | Users, BCrypt, lifecycle orchestration; `usersdb` |
| currency-exchange | 8000 | Public fiat rates via Frankfurter |
| currency-conversion | 8100 | USER fiat conversion; atomic bank swap via Feign |
| bank-account | 8200 | Fiat holdings and operation journal; `bankdb` |
| crypto-wallet | 8300 | Crypto holdings and operation journal; `walletdb` |
| crypto-exchange | 8400 | Public crypto / EUR / USD rates via Coinbase |
| trade-service | 8600 | USER trading, three-attempt rate retry, compensation |
| service-library | — | DTOs, asset catalog, roles and Feign contracts; library only |
| util | — | Shared validation, authorization helpers, internal authentication and global JSON errors; library only |
| frontend | 5173 | React interface; nginx in Docker |

Each executable service registers with Eureka. Each database is private to its service. Business inter-service calls use Feign and discovery, with no direct cross-database access. Gateway uses a discovery-aware reactive WebClient solely to validate Basic credentials, as appropriate for its reactive stack. External adapters use modern Spring `RestClient`, never `RestTemplate`.

## Authorization

| Function | OWNER | ADMIN | USER | Anonymous |
|---|---|---|---|---|
| Fiat / crypto rates | Yes | Yes | Yes | Yes |
| Users | CRUD | View; create/update USER only | No | No |
| Bank / wallet | No | View all; add/update holdings | View own only | No |
| Conversion / trade | No | No | Yes | No |

Basic Auth is enforced at Gateway for every protected endpoint. `/auth/me` validates login and returns only `{id,email,role}`. Gateway strips caller-supplied identity and internal headers, then injects verified email/role and a Gateway key. Services reject untrusted direct protected requests. `/internal/**` requires a separate shared service key and has no Gateway route. Internal mutation operations cannot be invoked as arbitrary USER balance edits. CORS permits `http://localhost:5173` by default.

Basic credentials are held in browser session storage, cleared on logout, and never stored in local storage. Changing your own user record signs you out so credentials and role are revalidated.

## Complete Gateway URL inventory

All user-facing backend URLs use **http://localhost:8765**. Replace `{id}` and `{email}` with actual values; URL-encode path values. All methods below except public rates and health require the `Authorization: Basic …` header.

| Method | Functional URL | Access / purpose |
|---|---|---|
| GET | `http://localhost:8765/auth/me` | Any authenticated role; current identity |
| GET | `http://localhost:8765/users` | OWNER / ADMIN; user directory |
| GET | `http://localhost:8765/users/{id}` | OWNER / ADMIN; user details |
| POST | `http://localhost:8765/users` | OWNER; ADMIN restricted to USER |
| PUT | `http://localhost:8765/users/{id}` | OWNER; ADMIN only USER → USER |
| DELETE | `http://localhost:8765/users/{id}` | OWNER; dependent cleanup before deletion |
| GET | `http://localhost:8765/bank-accounts` | ADMIN; all accounts |
| GET | `http://localhost:8765/bank-accounts/me` | USER; own fiat account |
| GET | `http://localhost:8765/bank-accounts/{email}` | ADMIN or matching USER |
| POST | `http://localhost:8765/bank-accounts/holdings` | ADMIN; add a new holding |
| PUT | `http://localhost:8765/bank-accounts/holdings` | ADMIN; set an existing total balance |
| GET | `http://localhost:8765/crypto-wallets` | ADMIN; all wallets |
| GET | `http://localhost:8765/crypto-wallets/me` | USER; own wallet |
| GET | `http://localhost:8765/crypto-wallets/{email}` | ADMIN or matching USER |
| POST | `http://localhost:8765/crypto-wallets/holdings` | ADMIN; add a new holding |
| PUT | `http://localhost:8765/crypto-wallets/holdings` | ADMIN; set an existing total balance |
| GET | `http://localhost:8765/currency-exchange?from=EUR&to=RSD` | Public fiat rate |
| GET | `http://localhost:8765/currency-exchange/assets` | Public supported fiat codes |
| GET | `http://localhost:8765/crypto-exchange?from=BTC&to=ETH` | Public crypto rate; EUR and USD also accepted |
| GET | `http://localhost:8765/crypto-exchange/assets` | Public supported crypto codes |
| GET | `http://localhost:8765/currency-conversion?from=EUR&to=RSD&quantity=100` | USER; mutates fiat holdings |
| GET | `http://localhost:8765/trade-service?from=EUR&to=BTC&quantity=100` | USER; buys BTC with EUR |
| GET | `http://localhost:8765/trade-service?from=ETH&to=BTC&quantity=0.01` | USER; crypto → crypto |
| GET | `http://localhost:8765/trade-service?from=RSD&to=BTC&quantity=100` | USER; RSD → USD conversion → BTC |
| GET | `http://localhost:8765/trade-service?from=ETH&to=EUR&quantity=0.01` | USER; crypto → fiat |
| GET | `http://localhost:8765/trade-service?from=ETH&to=RSD&quantity=0.01` | USER; ETH → USD → RSD conversion |
| GET | `http://localhost:8765/actuator/health` | Public Gateway health |

The last two business endpoints intentionally use GET because the assignment requires that exact request shape. **They change balances; submit once.** Gateway sends `Cache-Control: no-store`. The frontend never retries a mutation automatically.

Create a user (JSON, OWNER or ADMIN):

```json
{"email":"new@soas.local","password":"Password123!","role":"USER"}
```

Update a user (OWNER, or ADMIN updating a USER); `password:null` retains the current password:

```json
{"email":"renamed@soas.local","password":null,"role":"USER"}
```

Add / update a holding (ADMIN; use POST only when the currency is absent):

```json
{"email":"user@soas.local","currency":"USD","amount":"1000.00"}
```

Conversion/trade returns `{from,to,quantity,received,bankAccount,wallet,report,operationId}`. Crypto-target trades return the resulting wallet; fiat-target trades and conversions return the resulting bank account. The transaction report states both amounts and currencies. Amounts use `BigDecimal` with up to 12 fractional digits and half-even rounding. Codes are normalized to uppercase; emails to lowercase.

Errors use `{timestamp,status,error,message,path}` with appropriate 400/401/403/404/409/503 statuses and no client stack traces. Unexpected failures produce a generic 500 response and are logged server-side.

## External providers

* Fiat: [Frankfurter v2](https://frankfurter.dev/), `GET https://api.frankfurter.dev/v2/rate/EUR/RSD`, configurable with `FIAT_API_URL`.
* Crypto: [Coinbase public exchange rates](https://docs.cdp.coinbase.com/coinbase-app/track-apis/exchange-rates), `GET https://api.coinbase.com/v2/exchange-rates?currency=BTC`, configurable with `CRYPTO_API_URL`. No API key is required.

Both exact endpoints were verified during implementation. Provider adapters are isolated and tested against local HTTP stubs. Automated tests never call live providers. Rates are indicative quotes; this is an academic simulator with no real money or crypto custody.

Supported fiat: EUR, USD, RSD, GBP, CHF, JPY, CAD, AUD, CNY, HUF, BAM, NOK, SEK, PLN. Supported crypto: BTC, ETH, SOL, ADA, XRP, LTC, DOGE, BCH. Unsupported pairs and upstream outages return clear errors. Equal-asset lookup returns 1 without a network call.

## Tests and verification

```powershell
mvn clean test
mvn clean package
cd frontend
npm install
npm test
npm run build
cd ..
.\scripts\smoke-test.ps1
# Linux/macOS: bash scripts/smoke-test.sh
```

The smoke script requires the full running system, Node 22, demo credentials and live provider connectivity. It creates a disposable test USER, funds it through ADMIN endpoints, exercises conversion and every trade mode, checks authorization and lifecycle updates, then deletes it. Results go to ignored `.runtime/smoke-results.json`.

The final suite contains **94 backend tests** and **11 frontend tests**. A clean `npm ci` from the included lockfile and `npm audit` also pass, with zero reported vulnerabilities at verification time.

Tests include real H2 persistence, role and lifecycle rules, duplicate OWNER/email, atomic swaps and idempotent undo, mocked provider behavior, all trade paths, compensation and retry, Gateway HTTP routing/header stripping/authentication/CORS, frontend route protection, logout and API error handling. See [actual verification results](docs/VERIFICATION.md) for environment-specific outcomes; source presence alone is not runtime verification.

## Docker Hub submission

Set `DOCKERHUB_USERNAME` in `.env` to **your own** account. Each backend image is tagged `latest`:

```text
<username>/soas-naming-server:latest
<username>/soas-api-gateway:latest
<username>/soas-users-service:latest
<username>/soas-currency-exchange:latest
<username>/soas-currency-conversion:latest
<username>/soas-bank-account:latest
<username>/soas-crypto-wallet:latest
<username>/soas-crypto-exchange:latest
<username>/soas-trade-service:latest
```

The optional frontend image is `<username>/soas-frontend:latest`. Compose builds all images locally as well as assigning the submission tags. Dockerfiles are multi-stage, use Java 17 and run backend applications as a non-root user.

```powershell
docker login
docker compose build
docker compose push
```

Login/push require your credentials and have not been performed by this implementation. Configure the assigned university GitHub remote separately; do not push to a guessed remote.

## Explicit specification interpretations

1. The Crypto Wallet paragraph's “bank account with 0 ETH” means **wallet ETH = 0**.
2. One logical bank account/wallet per email has multiple currency rows with a database `unique(email,currency)` constraint.
3. Deletion is an authenticated **internal lifecycle operation**; ADMIN receives no public bank/wallet delete endpoint.
4. Fiat/crypto rate lookup is public to satisfy the UI requirement. Every other user-facing business function requires Gateway Basic Auth.
5. Non-EUR/USD trades settle through **USD**. Conversion is a real Feign call that mutates the intermediate holding; the trade then consumes it exactly once.
6. The OWNER uniqueness rule means at most one OWNER, including role updates. OWNER may update/delete any user, including themself, as explicitly required. Do not remove your only OWNER during the defense unless demonstrating recovery by restarting the in-memory system.
7. Fiat precision is also 12 decimal places to keep trade bridge calculations exact within the chosen precision. Presentation formats fiat to four and crypto to twelve decimal places; calculations happen on the backend.

## Troubleshooting

* **Java 8 starts instead of 17:** set `JAVA_HOME` and prepend its `bin` as shown above. `mvn -version` must report Java 17.
* **Docker cannot connect:** start Docker Desktop and wait for the Linux engine. `docker version` must include a Server section. Compose configuration validation does not prove containers ran.
* **Initial 503 or demo USER unavailable:** wait for Eureka registration and the scheduled demo provisioner (15-second initial delay, 10-second retries). Check service health, `.runtime/*.log` or Compose logs.
* **Public rate failure:** check internet access/provider limits. There are no fabricated fallback rates. Trading retries only rate reads, up to three attempts; balances are untouched if quotes cannot be obtained.
* **403:** check the role matrix. OWNER/ADMIN intentionally cannot convert or trade; OWNER cannot manage balances.
* **CORS:** use `http://localhost:5173`, or set `FRONTEND_ORIGIN` consistently. Build `VITE_API_URL` into the frontend when changing the Gateway address.
* **Lifecycle compensation error:** a user remains locked when automatic restoration fails. Restore downstream availability, inspect logs and operation IDs; use the protected internal contracts described in the architecture document to reconcile state. H2 restarts reset the entire demo data set.
* **Balance conflict:** concurrent updates fail rather than silently overwrite a balance; refresh before another transaction.
* **Memory:** allow roughly 3–4 GB for all JVMs, Eureka, Docker and the UI. Local scripts cap each JVM heap at 256 MB.

## Course submission reminders

Ultimately submit the complete code to the repository assigned under [SOAS-2025-26](https://github.com/SOAS-2025-26). Per the supplied specification, register the project defense at least **7 days before the exam date**; contact **vfficial98@gmail.com** for registration/questions. Minimum score to defend/pass: **51/100**. These administrative facts are recorded from the supplied guide; confirm current arrangements with the course staff.

