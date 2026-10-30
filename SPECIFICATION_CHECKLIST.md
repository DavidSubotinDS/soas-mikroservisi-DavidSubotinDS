# Specification checklist

This maps the supplied guide to code and evidence. “Implemented” describes source functionality; runtime and Docker evidence are recorded separately in [docs/VERIFICATION.md](docs/VERIFICATION.md). Points below are the assignment's allocation, not a claimed grade.

| Requirement / points | Implementation | Automated verification | Status |
|---|---|---|---|
| Users — **5** | `users-service/.../UserService.java`, `UserController.java`, `UserEntity.java` | `UserServiceTest`: create USER/ADMIN, BCrypt auth, duplicate email, second OWNER on create/update, ADMIN escalation/delete denied, USER denied, defaults, cleanup, email/role lifecycle, rollback | Implemented; suite passed |
| Currency Conversion — **5** | `currency-conversion/.../ConversionService.java`, `ConversionController.java` | `ConversionTest`: calculation, target state, insufficient/missing holding, invalid quantity, undo, all roles; bank integration tests exercise actual target creation | Implemented; suite passed |
| Bank Account — **5** | `bank-account/.../AccountService.java`, controller, JPA repositories/entities | `AccountServiceTest`: EUR default, ownership, ADMIN management, OWNER denied, USER own-only, negative/insufficient balance, updates, idempotency, swap/undo, rename/delete | Implemented; suite passed |
| Crypto Wallet — **5** | `crypto-wallet/.../AccountService.java`, controller, JPA repositories/entities | `AccountServiceTest`: ETH default, same role and lifecycle/balance cases as bank | Implemented; suite passed |
| Crypto Exchange — **5** | `crypto-exchange/.../ExchangeService.java`, `HttpRateProvider.java` | `ExchangeTest`, `HttpRateProviderTest`: major crypto, EUR/USD settlement, invalid asset/rate, equal pair, exact HTTP contract, outage/malformed data | Implemented; suite passed |
| Trade — **15** | `trade-service/.../TradeService.java`, `TradeController.java` | `TradeTest`: crypto/crypto, EUR/crypto, USD/crypto, RSD/USD/crypto, crypto/EUR, crypto/USD, crypto/USD/RSD, insufficient balances, invalid quantity, roles, compensation | Implemented; suite passed |
| Service Library + Util — **10** | `service-library/.../contract/`: DTOs, roles, seven Feign interfaces; `util/...`: reusable advice/security/validation | `ErrorAndAccessTest`, `ServiceSecurityTest` plus all service/Gateway tests | Implemented; suite passed |
| Docker — **15** | Nine backend Dockerfiles, frontend Dockerfile/nginx, `docker-compose.yaml`, `.env.example`, `.dockerignore` | `docker compose config --quiet`; actual build/runtime outcome in verification report | Configuration validated; runtime outcome recorded separately |
| User Interface — **30** | `frontend/src/`: public rates, login, directory, balances, management, conversion, all trade modes, role navigation, confirmation, loading/errors | `App.test.tsx`, `api.test.ts`; TypeScript/Vite production build; browser QA | Implemented; 11 tests and build passed |
| Additional specification / fault tolerance — **5** | `trade-service/.../RateRetry.java`, transaction compensation and balance journals | `TradeTest`: transient recovery, 3-attempt exhaustion without mutation, reverse undo and failed compensation | Implemented; suite passed |
| **Total available — 100** | | | Evaluation belongs to course staff |

## Mandatory infrastructure

| Requirement | Implementation | Evidence / status |
|---|---|---|
| Eureka Naming Server | `naming-server`, port 8761; all nine apps have Eureka config | Compiles/packages; live registry outcome in verification report |
| API Gateway | `api-gateway`, port **8765**, seven discovery-based business routes | `GatewayTest`: required routes and identity reach local HTTP downstream |
| Basic Auth at Gateway | `GatewaySecurity` reactive manager → users internal BCrypt contract | `AuthenticationContractTest`, `GatewayTest`: missing/invalid 401, forbidden 403, identity without password |
| Trusted identity | `IdentityFilter`, `util/ServiceConfiguration` | Gateway test strips spoofed headers and Authorization; internal URLs denied |

## Full requirement map

| Guide section | Source / design location | Test / verification | Status |
|---|---|---|---|
| 1. Source of truth, versions, layout, nine services, libraries, uppercase codes, decimal money | Root POM; all module POMs; `Assets`, `Checks`; preserved `docs/SPECIFICATION.md` | Root reactor compilation, validation tests, verification report | Implemented |
| 2. Maven/Java/Docker/H2/React and database-per-service | Root and child POMs, separate usersdb/bankdb/walletdb YAML, Dockerfiles, React/Vite | Real H2 service tests, full backend/frontend build | Implemented |
| 3. Feign business communication; no RestTemplate | `service-library/*Client.java`; Gateway WebClient; two external RestClient adapters | Contract tests, source inspection, live smoke | Implemented |
| 4. Shared service library | Shared DTO records and seven Feign interfaces; no JPA entities in library | Reactor compilation and service tests | Implemented |
| 5. Global exception management | `util/GlobalErrors`, `BusinessException`, `ApiError`, `Checks`; reactive `GatewayErrors` | `ErrorAndAccessTest`, provider/validation tests, smoke JSON checks | Implemented |
| 6. Mandatory naming server | `naming-server` and Eureka configuration in every app | Packaging; full runtime registration check | Implemented |
| 7. Users and every role/lifecycle rule | Users service, unique email and owner slot, BCrypt, ready flag, Feign cleanup/restore/rename/provision | 15 `UserServiceTest` cases plus two `DemoDataTest` cases; smoke lifecycle | Implemented |
| 8. Public fiat exchange, real API, equal rate=1 | `currency-exchange` Frankfurter adapter, public Gateway route | 8 fiat tests; verified exact live endpoint | Implemented |
| 9. USER-only mandatory fiat conversion GET | Conversion controller/service → FiatClient and BankClient atomic swap | 6 conversion tests and bank integration; Gateway route test; smoke | Implemented |
| 10. Bank data, USER owner, EUR0, safe operations, roles | Bank JPA and journal, Feign USER lookup, public/internal separation | 11 bank tests; lifecycle and smoke | Implemented |
| 11. Wallet data, USER owner, ETH0, safe operations, roles | Wallet JPA and journal, Feign USER lookup, public/internal separation | 11 wallet tests; lifecycle and smoke | Implemented |
| 12. Crypto API with BTC/ETH and EUR/USD rates | Coinbase adapter; replaceable RateProvider and configurable base URL | 9 crypto tests; verified exact live endpoint | Implemented |
| 13. All trade categories, bridge conversions, result/report, compensation | Trade service, `ConversionClient`, per-operation undo journal | 17 trade tests including all seven paths and failures; smoke | Implemented |
| 14. Required retry/circuit breaker | Three-attempt retry on rate reads only | Trade retry recovery/exhaustion tests | Implemented using explicitly permitted retry |
| 15. Gateway, Basic, CORS, exact URLs, 401/403 | Security, routes, identity filter, `/auth/me`, CORS config | 9 Gateway/authentication tests, including HTTP routing | Implemented |
| 16. Exact authorization matrix | Gateway rules, `Access`, user-service role checks | Gateway, users, accounts, conversion and trade tests; frontend guard tests | Implemented |
| 17. React UI for every business service | Seven screens with public rates, role navigation, forms, reports and destructive-action confirmation | 11 frontend tests; production build; browser QA | Implemented |
| 18. Separate H2 DBs, constraints, no shared DB | Three service YAML files, unique row constraints and optimistic versions | Real H2 persistence tests | Implemented |
| 19. Deterministic demo roles and funded USER | `DemoData`, managers, idempotent funding | User/service tests; full runtime demo login | Implemented |
| 20. Configurable isolated external adapters, no secrets | FIAT_API_URL / CRYPTO_API_URL, two `RateProvider` interfaces | Local HTTP stubs; live endpoint verification | Implemented |
| 21. Nine latest-tag Docker Hub images and local Compose | Dockerfiles, Compose image names, DOCKERHUB_USERNAME, README commands | Compose config validated; actual build/runtime in report | Publishing awaits user's Docker Hub credentials |
| 22. Meaningful tests; clean test/package; frontend build | Tests in each business module, Gateway and util; frontend tests | Exact commands and outcomes in verification report | Backend/frontend test and build passed |
| 23. Mandatory all Gateway URLs + demo credentials | README complete method/path inventory and credentials table | Compared against all controller mappings | Documented |
| 24. Checklist and point values | This document, all 100 points plus mandatory infrastructure | Manual requirements review | Documented |
| 25. Course submission requirements | README: university org, 7 days, contact, 51 points | Copied from supplied guide; not application behavior | Documented; remote and defense registration are manual |
| 26. Explicit ambiguity handling | README interpretations; architecture notes | Defaults, ownership and auth tests | Documented and implemented |
| 27. Quality, constructors, DTOs, validation, no stack traces/secrets | All service layers, shared validation/advice, `.gitignore` | Builds/tests, source review | Implemented |
| 28. Development support | Cross-platform start/stop/smoke scripts; Mermaid architecture | Smoke and script execution outcomes in report | Implemented |
| 29. Iterative implementation and builds | Foundation → business services → Gateway → UI → deployment | Verification log summary | Followed |
| 30. Definition of done including runtime | All source plus runtime checks | `docs/VERIFICATION.md` distinguishes passed vs unavailable checks | See report; external submission steps remain manual |
| 31. Inspect, implement, fix failures, concise honest handoff | Empty repo inspected, no remote/push, work restricted to SOAS | Git status and final report | Followed |

## Manual submission actions

* Configure only the **assigned and verified** university repository under SOAS-2025-26, then submit this code there. No clone/Classroom enrollment/remote guessing was performed.
* Set the user's own Docker Hub name, log in, and push all required `latest` images. No credentials or pushes are claimed here.
* Register the defense at least **7 days before the exam date**, per the supplied guide, using **vfficial98@gmail.com**. Confirm current details with course staff.
* The supplied minimum for defense/pass is **51 points**. This checklist does not assert an awarded score.


