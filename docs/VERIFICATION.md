# Local verification record

Performed on **2026-10-29**, Europe/Budapest, in the SOAS workspace. No Git remote was configured, no Git history was rewritten, and no GitHub or Docker Hub push was made.

## Passed checks

| Check | Actual result |
|---|---|
| Root Maven `clean test` | All backend tests passed; final count is recorded below |
| Root Maven `clean package` | All eleven modules built; nine executable Boot jars were produced |
| Frontend `npm install` | Dependencies installed; package-lock.json retained |
| Final frontend `npm ci` | Clean installation from the final lockfile passed using the installed npm 10 |
| Final frontend `npm audit` | **0 known vulnerabilities** after same-major Router, Vite and Vitest updates |
| Frontend `npm test` | **11 tests passed**, two test files |
| Frontend `npm run build` | TypeScript checking and Vite production build passed |
| `docker compose config --quiet` | Passed; image names, interpolation, service dependencies and configuration parse correctly |
| Native Java service startup | All nine packaged applications started successfully |
| Fresh-start demo provisioning | Passed after fixing the Eureka discovery race; regression tests cover delayed discovery and resuming pending demo provisioning |
| Eureka registry | **All nine services registered UP**, including naming-server |
| Gateway | Started at **http://localhost:8765**; real Basic credentials validated through users-service |
| End-to-end Gateway smoke | **12/12 groups passed**, using real external quotes and disposable USER data |
| Browser QA | Public live rate lookup, demo USER login, own balance display and simulated EUR→BTC trade succeeded |
| Responsive browser QA | 390×844 mobile layout and navigation inspected; no horizontal page overflow; desktop restored |
| Source inspection | No RestTemplate in Java sources; WebClient only in Gateway authentication; business interactions use Feign |

The smoke test covers public fiat/crypto rates, missing/invalid Basic credentials, forbidden roles, automatic EUR0/ETH0, ADMIN funding and restrictions, USER ownership, fiat conversion, crypto→crypto, EUR→crypto, USD→crypto, RSD→USD→crypto, crypto→EUR, crypto→USD, crypto→USD→RSD, insufficient funds/invalid inputs, email migration, role migration, and deletion cleanup. The disposable test account is removed afterward.

A cold-start live rate request encountered one transient Frankfurter timeout and returned a clean 503. A subsequent full smoke run passed all 12 groups. Public rates still depend on provider availability; no fallback prices are fabricated. The final production UI was also verified after the tooling updates, including a successful trade, logout and a public quote.

Backend automated tests use H2 and mocked Feign clients/local HTTP provider stubs. They do not depend on internet access. Gateway tests run a real random-port WebFlux server and local HTTP downstream with discovery-based routes.

## Environment details

* Windows 11; Maven 3.9.8; Eclipse Adoptium JDK 17.0.15; Node 22.15.0; npm 10.9.2.
* Final frontend tooling: Vite 7.3.7, Vitest 4.1.12, React Router 7.18.5. A transient workspace-cached npm 11 process resolved an npm 10 dependency-update bug; no global npm update was made. Clean `npm ci`, tests, audit and build then passed with the installed npm 10.
* The machine's default `java` command initially resolved to Java 8. Maven and startup scripts used `JAVA_HOME` pointing at JDK 17.
* Maven and npm caches used ignored `.tools/` directories inside the SOAS workspace. Network-enabled execution was needed to download dependencies and reach local/live HTTP endpoints.
* Native JVMs were capped at 256 MB heap each. Startup/discovery and asynchronous demo provisioning completed before smoke testing.

## External endpoints actually verified

* `https://api.frankfurter.dev/v2/rate/EUR/RSD` returned HTTP success and a JSON pair rate (117.71 at the verification time).
* `https://api.coinbase.com/v2/exchange-rates?currency=BTC` returned BTC rates including ETH, EUR and USD.
* Rates can change. Those observations verify adapter contracts, not fixed market expectations. Tests use deterministic mocked rates.

## Docker verification limit — incomplete

Docker CLI **28.3.3** is installed. The Linux engine was initially stopped. Starting the existing Docker Desktop installation encountered an existing host startup failure:

```text
starting services: initializing Inference manager:
listening on unix://<HOME>/AppData/Local/Docker/run/dockerInference:
The file cannot be accessed by the system.
```

`docker version` could not obtain an operational Server response. An actual `docker compose build` attempt timed out waiting for the unavailable engine and was stopped after a bounded wait. **No successful Docker image build or Compose container smoke test is claimed.** Only `docker compose config --quiet` passed. Docker's application data and global settings were not reset or modified to work around the error.

Once Docker Desktop is repaired/running, execute:

```powershell
Copy-Item .env.example .env
# Enter your own DOCKERHUB_USERNAME in .env.
docker compose build
docker compose up -d --wait --wait-timeout 240
.\scripts\smoke-test.ps1
docker compose ps
docker compose down
```

Do not run Compose on ports 8761/8765/5173 while the native project services or Vite are already using those ports. Stop native services and Vite first.

## Evidence and remaining manual actions

Screenshots: [public rates](screenshots/public-rates.jpg), [successful USER trade](screenshots/user-trade.jpg), [mobile trade](screenshots/mobile-trade.jpg). Raw local build, smoke, registration and runtime logs are ignored under `.tools/` and `.runtime/` rather than committed.

Manual actions still required: repair/start Docker Desktop and complete image/container verification; configure the assigned university GitHub remote; set the user's own Docker Hub name, log in and push the required `latest` images; register the defense with the course staff. The application, tests and documentation do not require any API secrets.

All native SOAS JVMs and the frontend preview started for verification were stopped after the final checks. Use the README startup commands to run the project again.

Final backend test count: **94 passed, 0 failures, 0 errors, 0 skipped**. This includes internal-service authentication, simultaneous debits, precision overflow, all lifecycle and role cases, provider adapters, transaction compensation/retry, Gateway HTTP tests, and demo-startup discovery recovery.


