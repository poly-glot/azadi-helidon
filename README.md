# Azadi Financial Services -- Customer Portal (Helidon)

Online finance portal for Azadi, an imaginary luxury car manufacturer. Customers sign in with their agreement number,
date of birth and postcode to view their agreement, make a card payment, request a settlement figure or a statement,
change their payment date, and keep their contact and bank details up to date.

This is a port of the [Spring Boot portal](https://github.com/poly-glot/azadi) to **Helidon 4 SE**. Same pages, same
templates, same Datastore kinds, same encrypted bank details. The point of the port was footprint: the application
runs in about 150 MB of resident memory on a plain JVM, inside a 98 MB (compressed) container image, with no GraalVM
native build. The investigation that led there, and the numbers, are in [Design Decisions](#design-decisions).

Helidon WebServer with Thymeleaf for rendering, Firestore in Datastore mode for persistence, Stripe for card payments,
Resend for email. Deployed to Cloud Run behind Firebase Hosting, with the infrastructure declared in Terraform.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Development Setup](#development-setup)
- [Building and Testing](#building-and-testing)
- [Deployment](#deployment)
- [Environment Variables](#environment-variables)
- [Design Decisions](#design-decisions)
- [Measurements](#measurements)
- [Known Gaps](#known-gaps)

---

## Architecture Overview

### Request flow

1. The **browser** reaches Firebase Hosting, which serves nothing itself here: every path is rewritten to the Cloud Run
   service. Hosting adds a one-year cache lifetime to the hashed assets under `/assets/`, which the application serves
   with ETags.
2. **Cloud Run** runs one instance of the Helidon server (sessions are in memory, so the service is pinned to a single
   instance with session affinity).
3. The **security filter** runs before every route: it resolves the session cookie, applies the rate limits, issues the
   per-request CSP nonce and security headers, checks the CSRF token on every state-changing request, and sends
   anonymous users of private pages to the login page.
4. A **controller** reads the request, calls a **service**, and renders a Thymeleaf template through `Web.page`, which
   assembles the common model (customer name, nonce, CSRF token, flash messages, asset paths).
5. **Services** hold the rules and talk to **repositories**, each of which maps one Datastore kind to a Java record.
   Sensitive operations are written to the audit trail and, where the Spring app did so, confirmed by email.
6. **Stripe Elements** collects card details in the browser; the server creates the payment intent and later receives
   the signed webhook that marks the payment completed or failed.

### Pages

| Route                          | Controller              | Description                                          |
|--------------------------------|-------------------------|------------------------------------------------------|
| `/login`                       | `AuthController`        | Agreement number, date of birth and postcode login   |
| `/my-account`                  | `AgreementController`   | Agreement, balance and payment details               |
| `/agreements/{id}`             | `AgreementController`   | Agreement detail                                     |
| `/my-documents`                | `DocumentController`    | Document list                                        |
| `/my-contact-details`          | `ContactController`     | Inline-editable address, phone and email             |
| `/finance/make-a-payment`      | `PaymentController`     | Stripe Elements card payment                         |
| `/finance/settlement-figure`   | `SettlementController`  | Settlement quote, valid for 28 days                  |
| `/finance/change-payment-date` | `PaymentDateController` | Payment date change, once per agreement              |
| `/finance/request-a-statement` | `StatementController`   | Statement request                                    |
| `/finance/update-bank-details` | `BankDetailsController` | Encrypted bank detail update with email confirmation |
| `/help/*`, `/cookies`, `/privacy`, `/terms` | `HelpController` | Help and legal pages                           |
| `/api/stripe/webhook`          | `PaymentController`     | Stripe webhook (signature-verified, CSRF exempt)     |
| `/actuator/health`             | Helidon observe         | Health check used by Cloud Run and the pipeline      |

---

## Project Structure

```
azadi-helidon/
+-- src/main/java/guru/junaid/azadi/
|   +-- Main.java               # Wiring by hand, routing table, server start
|   +-- Train.java              # Build-time: runs the app once to record the class-data archive
|   +-- Ping.java               # Floor measurement: a bare WebServer with one route
|   +-- agreement/              # Agreement record, repository, service, controller, dto/
|   +-- audit/                  # Audit trail
|   +-- auth/                   # Customer, login, lockout tracker, in-memory sessions
|   +-- bank/                   # Bank details, encryption (Encryptors.delux), validation
|   +-- contact/                # Contact details
|   +-- document/               # Documents
|   +-- email/                  # Resend client and branded HTML templates
|   +-- help/                   # Help and legal pages
|   +-- payment/                # Payment intents, webhook handler, payment date change
|   +-- settlement/             # Settlement figures
|   +-- statement/              # Statement requests
|   +-- seed/                   # DataSeeder: demo data, run as the migration job
|   +-- common/                 # Db + Records (Datastore access), Web, ErrorHandler, Views (Thymeleaf), Money, Ordinal
|   +-- config/                 # SecurityFilter, RateLimiter, Cookies
+-- src/main/resources/
|   +-- application.properties  # Defaults; every key can be overridden by its environment variable
|   +-- templates/              # Thymeleaf layout, fragments and pages (identical to the Spring app)
|   +-- static/                 # Vite build output: hashed CSS, JS and images
|   +-- seed/customers.json     # The five demo customers
+-- src/test/java/guru/junaid/azadi/
|   +-- <slice>/                # Unit tests: services with mocked repositories, validation, encryption, sessions
|   +-- web/                    # Controller tests on Helidon's routing test support with the real templates
|   +-- *IntegrationTest        # The real application against the Firestore emulator and in-process Stripe/Resend fakes
+-- frontend/                   # Vite project: PostCSS layers, vanilla JS, images
+-- quality/                    # Checkstyle, PMD, SpotBugs and OWASP rulesets
+-- scripts/                    # jlink, run, seed, fakes, HTTP/route/Chrome checks, memory measurement, container test
+-- compose.yml                 # Firestore emulator
+-- Dockerfile                  # Maven build, jlink, class-data training, Alpine runtime
+-- firebase.json               # Hosting rewrites and Datastore index definitions
+-- .devcontainer/              # Java 21, Node 22, gcloud, Docker
+-- .github/workflows/          # ci.yml (gates and tests), cd.yml (Cloud Run and Hosting)
```

### Vertical slices

Each feature package is self-contained: the entity record, a repository for its Datastore kind, a service holding the
rules, a controller exposing `routing(HttpRules)`, and a `dto/` package where the page needs a different shape from the
entity. Cross-cutting pieces live in `common/` and `config/`. There is no dependency injection container; `Main`
constructs every object once and registers the controllers in a list. The layout is deliberately the same as the
Spring app's, so a reader of one can find their way around the other.

---

## Development Setup

### Prerequisites

- Docker and Docker Compose
- VS Code with the Dev Containers extension, or a local JDK 21, Maven 3.9 and Node 22

### Quick start in the dev container

Open the folder in VS Code and choose "Reopen in Container". The container provides Java, Node, Maven, gcloud and
Docker, and defines the shell aliases used below.

```bash
fb-emulator              # Terminal 1: Firestore emulator on :8081, Datastore mode
fakes                    # Terminal 2: fake Stripe on :12111, GCE metadata on :12112, Resend on :12113
./scripts/jlink.sh       # Terminal 3, once: trimmed JRE in target/jre
seed && dev              # seed the demo customers, build, run on :8080
```

Outside the container, start the emulator with `docker compose up -d`, then run the same steps directly:

```bash
mvn -B package -DskipTests
node scripts/fakes.mjs &
./scripts/jlink.sh
./scripts/seed.sh        # does nothing if customers already exist
./scripts/run.sh         # environment from scripts/dev.env
```

Open http://localhost:8080 and use the "Login as demo user" link, or any customer from the table below. The emulator
keeps its data in memory; restart it for a clean slate, then run `seed` again.

`scripts/dev.env` carries dummy keys and the encryption key the seeder uses. The application reads the same file, so the
bank details it seeds decrypt at startup (the log line `crypto round-trip ok=true` confirms it).

### Demo customers

| Agreement    | Name           | Date of birth | Postcode   |
|--------------|----------------|---------------|------------|
| `AGR-100001` | James Wilson   | `15/3/1985`   | `SW1A 1AA` |
| `AGR-100002` | Sarah Thompson | `22/7/1990`   | `M1 1AE`   |
| `AGR-100003` | David Patel    | `3/11/1978`   | `B1 1BB`   |
| `AGR-100004` | Emma Roberts   | `28/1/1992`   | `LS1 1BA`  |
| `AGR-100005` | Michael Chen   | `10/6/1988`   | `EH1 1YZ`  |

### Seed data

Everything the pages need comes from `src/main/resources/seed/customers.json`: for each customer, the agreement, the
documents and the bank details. `DataSeeder` adds six months of completed payments and a settlement figure on top, and
encrypts the bank details with the configured key. Edit the JSON, reset the emulator and reseed; no Java is needed to
change the demo data.

### Frontend

CSS and JavaScript are built by Vite in `frontend/` and written straight into `src/main/resources/static`, with the
asset manifest the server reads at startup. The build output is committed, so the application builds and runs without
Node. After changing anything under `frontend/src`:

```bash
cd frontend && npm ci && npm run build
```

There is no Vite dev server integration in this port: templates always reference the hashed build output.

---

## Building and Testing

```bash
mvn -B verify                   # every gate and every test
mvn -B verify -P no-checks      # tests only
mvn -B test -P no-checks        # unit and controller tests only (no Docker needed)
mvn -B package -DskipTests      # application jar plus target/libs
```

### Quality gates

| Tool             | Scope                                               | Configuration                     |
|------------------|-----------------------------------------------------|-----------------------------------|
| Checkstyle       | Main and test sources, `validate` phase             | `quality/checkstyle.xml`          |
| PMD              | Main sources, every category                        | `quality/pmd-rules.xml`           |
| SpotBugs         | Main sources, maximum effort, low threshold         | `quality/spotbugs-exclude.xml`    |
| JaCoCo           | Unit and integration coverage merged, lines >= 75 % | `pom.xml`, `jacoco.line-coverage` |
| dependency-check | On demand: `mvn dependency-check:check -Dnvd.api.key=...` | `quality/owasp-suppressions.xml` |

The rulesets are the Spring app's. They live in `quality/` rather than under `src/main/resources` so that they never
end up inside the jar or the image.

### Tests

| Suite       | Runner   | What it covers                                                                                      |
|-------------|----------|-----------------------------------------------------------------------------------------------------|
| Unit        | surefire | Services with mocked repositories, request validation, encryption, sessions, lockout, rate limiter, the record mapper, email templates and the Resend client against a local HTTP server |
| Controller  | surefire | Every controller on Helidon's `@RoutingTest` with mocked services and the real templates: rendered HTML, redirects, flash messages, and what each service was asked to do |
| Integration | failsafe | The real application started by `Main.start(config)` against the Firestore emulator in a Testcontainer, with Stripe and Resend answered in process. Login and lockout, security headers, CSP nonce, CSRF, cookies, rate limits, every account and finance flow, and data isolation between customers |

The integration suite needs Docker. Two shared-state rules keep it honest: every test account gets its own agreement
number, because the lockout tracker is keyed by it, and every client sends its own `X-Forwarded-For`, because the rate
limiter is keyed by caller.

### Shell and browser checks

With the emulator, the fakes and the application running, the original verification scripts still apply and are what
CI runs after the Maven build:

```bash
./scripts/http-checks.sh      # 31 checks: headers, CSRF, sessions, rate limits, lockout, payments, webhooks
./scripts/route-checks.sh     # 46 checks: every route, ownership, validation, emails
./scripts/docker-test.sh      # the same checks against the built image, with memory readings at each stage
CDP=localhost:9222 node scripts/chrome-journey.mjs    # a Chrome started with --remote-debugging-port=9222
CDP=localhost:9222 node scripts/chrome-pages.mjs      # every page, no console, CSP or network errors
```

### Memory measurement

```bash
./scripts/floor.sh            # bare Helidon with one route
./scripts/measure.sh          # the app, RSS after each stage of traffic
./scripts/full-measure.sh     # trains the class-data archive first, then measures
```

These read `/proc`, so they run on Linux (the dev container) or against the container through `docker-test.sh`.

---

## Deployment

### Docker

```bash
docker build -t azadi-helidon .
docker run -p 8080:8080 --env-file scripts/dev.env \
  -e DATASTORE_HOST=host.docker.internal:8081 \
  -e STRIPE_API_BASE=http://host.docker.internal:12111 \
  -e RESEND_API_URL=http://host.docker.internal:12113/emails azadi-helidon
```

The overrides point the container at the emulator and the fakes running on the host; `scripts/docker-test.sh` does the
same and then runs the full verification against it.

The image is built in four stages. `build` compiles with Maven on `maven:3.9-eclipse-temurin-21-alpine` and links a
runtime with jlink. `base` copies the runtime, the jars and the application jar onto `alpine:3.21`. `train` starts the
application once with `-XX:ArchiveClassesAtExit` against an in-process fake Datastore, drives the anonymous paths and
the webhook, and stops it with SIGTERM to produce the class-data archive. `runtime` adds the archive and runs as a
non-root user with the tuned JVM flags. Because `train` and `runtime` share `base`, the jars are byte-identical and the
archive validates at startup.

### Cloud Run and Firebase Hosting

`.github/workflows/cd.yml` runs on every push to `main`:

1. The CI workflow: all quality gates, both test suites, the shell checks against a packaged jar, and a Docker build.
2. Build and push the image to Artifact Registry, tagged with the short commit hash.
3. Deploy a `staging` revision with no traffic. The deploy passes only the image; the service's environment, secrets,
   service account and scaling belong to Terraform.
4. Run the database migration (below).
5. Health-check the staging revision; the job fails if it does not answer 200.
6. Shift traffic to the new revision, deploy Firebase Hosting (site `azadi-helidon`, every path rewritten to the
   service) and the Datastore index definitions, and create a GitHub release.
7. Smoke-test the public site.

### Database migration

Firestore in Datastore mode has no schema, so "migration" covers two things. Composite index definitions live in
`firestore.indexes.json` and are deployed with the hosting release. Seed data is applied by the Cloud Run job
`azadi-helidon-api-migrate`, which the pipeline points at the freshly built image and executes before the smoke test.
The job runs `DataSeeder` with the same configuration and secrets as the service. It is idempotent: when a `Customer`
already exists it exits without writing, so every deploy runs it and only the first one seeds. To reseed, delete the
kinds and redeploy.

### Infrastructure

Everything the service needs in GCP is declared in `terraform/apps/azadi-helidon.tf` of the
[firebase-cloud](https://github.com/poly-glot/firebase-cloud) repository, using the same modules as the Spring and Go
portals: the `azadi-helidon` Datastore database, the `azadi-helidon-ci-cd` and `azadi-helidon-runtime` service
accounts and their roles, a Workload Identity provider restricted to this repository, the Firebase site, the Cloud Run
service and migration job with their environment and Secret Manager references, and the six secret shells
(`azadi-helidon-encryption-key`, `-encryption-salt`, `-stripe-api-key`, `-stripe-publishable-key`,
`-stripe-webhook-secret`, `-resend-api-key`). Secret values are the one thing added outside Terraform, with
`gcloud secrets versions add`.

The Terraform outputs `azadi_helidon_wif_provider` and `azadi_helidon_gcp_sa_email` are this repository's
`WIF_PROVIDER` and `GCP_SA_EMAIL` secrets. The Stripe webhook endpoint is
`https://azadi-helidon.web.app/api/stripe/webhook`, registered against the API version the SDK pins; Stripe events on
any other version would not deserialise.

---

## Environment Variables

Configuration is Helidon's `Config`. Defaults live in `src/main/resources/application.properties`; each key is
overridden by the environment variable of the same name in upper snake case, so `gcp.project.id` is set with
`GCP_PROJECT_ID`. The WebServer is bound to the `server` section, which means any listener setting can be changed the
same way (`SERVER_PORT`, `SERVER_HOST` and so on). `PORT`, the Cloud Run convention, is mapped onto `server.port`.

| Variable                      | Default               | Description                                            |
|-------------------------------|-----------------------|--------------------------------------------------------|
| `PORT`                        | `8080`                | HTTP port                                              |
| `GCP_PROJECT_ID`              | `demo-azadi`          | GCP project                                            |
| `FIRESTORE_DB`                | `azadi`               | Datastore database name                                |
| `DATASTORE_HOST`              | (none)                | Emulator host, e.g. `localhost:8081`                   |
| `USE_ADC`                     | `false`               | `true` on Cloud Run: credentials from the metadata server |
| `COOKIE_SECURE`               | `true`                | `false` for plain HTTP on localhost                    |
| `DEMO_MODE`                   | `false`               | Show the demo login link                               |
| `TEMPLATE_CACHE`              | `true`                | Cache compiled Thymeleaf templates                     |
| `RATE_LIMIT_GENERAL`          | `60`                  | Requests per minute per caller                         |
| `STRIPE_API_KEY`              | (none)                | Stripe secret key                                      |
| `STRIPE_WEBHOOK_SECRET`       | (none)                | Stripe webhook signing secret                          |
| `VITE_STRIPE_PUBLISHABLE_KEY` | (none)                | Stripe publishable key, rendered into the payment page |
| `STRIPE_API_BASE`             | (none)                | Stripe API override, used by the tests and the fakes   |
| `AZADI_ENCRYPTION_KEY`        | (none)                | Key for bank detail encryption                         |
| `AZADI_ENCRYPTION_SALT`       | (none)                | Hex salt for bank detail encryption                    |
| `RESEND_API_KEY`              | (none)                | Resend API key                                         |
| `RESEND_FROM_EMAIL`           | `noreply@junaid.guru` | Sender address                                         |
| `RESEND_API_URL`              | Resend's endpoint     | Override, used by the tests and the fakes              |

---

## Design Decisions

### Why Helidon SE, and why not a native image

The Spring Boot portal answers the cold-start problem on Cloud Run with a GraalVM native image. That works, but a
native build takes a quarter of an hour, needs its own reachability metadata, and the JVM target it leaves behind for
development runs in over 400 MB. The question this port set out to answer was whether a plain JVM could be small
enough instead.

Helidon SE was chosen because it is a web server rather than a framework: no classpath scanning, no proxies, no
reflection-driven wiring, and a small set of jars. The first spike put the same Thymeleaf templates and the same
Datastore client on it and measured. The result decided the matter:

| Configuration                                   | Startup | After load |
|-------------------------------------------------|---------|------------|
| Spring Boot jar, full JDK                       | 441 MB  | 443 MB     |
| Spring Boot jar, jlink runtime and tuned flags  | 265 MB  | 270 MB     |
| Helidon, jlink runtime, flags only              | 130 MB  | 230 MB     |
| Helidon with `MALLOC_ARENA_MAX=2` (glibc)       | 100 MB  | 175 MB     |
| Helidon with a 40 MB heap and tight free ratios | 97 MB   | 162 MB     |
| Helidon with a class-data archive               | 105 MB  | 144 MB     |
| Bare Helidon, one route, same flags             | 77 MB   | 82 MB      |

Roughly, the JVM and Helidon cost 80 MB, Thymeleaf 20 MB, the Stripe SDK 8 MB, the Datastore client 6 MB, and the heap
grows by 10 MB under load. A 150 MB budget is therefore achievable with the standard SDKs, and no native build is
needed. Cold start to the first rendered page is 1.3 s from `docker run`, which Cloud Run's startup CPU boost hides.

### Standard SDKs, and the parts that had to be written

The temptation in a memory-constrained port is to replace every library with something smaller. The rule applied here
was the opposite: use what the framework and the official SDKs provide, and only write code for what they do not
have, after measuring. `google-cloud-datastore` and `stripe-java` stayed. `spring-security-crypto` stayed too, as the
only Spring artifact, so that bank details encrypted by the Spring app with `Encryptors.delux` remain readable; the
startup self-check decrypts a seeded record to prove it.

Helidon SE genuinely has no HTTP session store, no per-caller rate limiter and no login-attempt tracker, so
`Sessions`, `RateLimiter` and `LoginAttemptTracker` are hand-written and small. It also has no Thymeleaf integration,
and Thymeleaf 3.1 has no non-servlet web adapter, so `Views` implements the `IWebExchange` interfaces that `@{...}`
link expressions require. One lesson from that adapter: the exchange's attribute map must be created per render,
because Thymeleaf writes template variables into it and a shared map leaks them between users.

Where Helidon does ship something, it is used. Configuration is Helidon `Config` with `application.properties` and
environment overrides, which also makes every WebServer listener setting configurable for free. The health endpoint
comes from `helidon-webserver-observe-health`. Cookies are read and written through Helidon's `SetCookie` and
`headers().cookies()`, and form bodies are parsed with `UriQuery`.

### Records and a reflective mapper instead of an ORM

Each entity is a Java record. `common/Records` maps a record to and from a Datastore entity by reflection over its
components (the component name is the property name, a `long id` is the key), and `Db` offers
`query(rows).where(..).first()` and `.list()`, `find` and `save`. The nine repositories therefore contain only their
query methods, and the mapping is tested once. Objectify and Spring Data were considered and rejected: the first wants
annotated mutable classes and a global registry, the second is what the port removed. A missing string property reads
as `null`, which is the honest value; the two call sites that cared were made null-safe.

### Thymeleaf templates kept verbatim

The templates are the Spring app's, unchanged, including the layout dialect. Keeping them identical is what makes the
port a port: the same markup, the same accessibility properties, the same visual regression surface. The cost is
Groovy, which the layout dialect is written in: a 7 MB jar and about 500 classes in the archive. Rewriting the
templates onto plain `th:replace` fragments would remove it, and was judged not worth losing template parity. OGNL
cannot see record accessors, so the DTO records that templates access with property syntax carry explicit getters.

### Sessions in memory

Sessions live in a map with a fifteen-minute idle timeout and one session per customer; signing in again elsewhere
ends the first session. This is the simplest correct implementation and it costs nothing at runtime, but it ties a
customer to a process. The Cloud Run service is therefore limited to one instance with session affinity. A session
store would be the first change to make if the portal ever needed to scale out.

### The container image

The runtime is a jlink image built from the modules `jdeps` finds, minus the debugger, compiler, instrumentation,
preferences and Kerberos modules that Groovy and the HTTP client reference but never use. `java.sql` has to stay
(Thymeleaf's expression utilities touch `java.sql.Date`) and so does `java.desktop` (OGNL uses `java.beans`); the AWT,
font and sound libraries that come with the latter are deleted after linking, as is the second class-data archive
jlink generates for uncompressed pointers. `libjvm.so` is stripped of its symbol table.

The Datastore client's gRPC transport is excluded. The client uses its HTTP transport here, and `DatastoreOptions`
links only the small gRPC API jars at class-load time, so the Netty, xDS and Conscrypt jars (27 MB) can go. Alpine
replaces Debian as the base: it is 9 MB instead of 108 MB, and the musl allocator does not need the
`MALLOC_ARENA_MAX` workaround that glibc did.

The class-data archive is 47 MB and the largest remaining lever. It holds 6,252 classes, of which about 500 are
Groovy and 800 are Stripe models trained through the webhook path. Both are kept on purpose: the first for template
parity, the second so that the first real webhook does not pay for loading the Stripe model graph into metaspace.

### Security posture

- Content Security Policy with a per-request nonce for scripts and styles, `default-src 'self'`, Stripe and Google
  Fonts as the only external origins, and a report endpoint.
- HttpOnly, Secure, SameSite=Strict session cookie named `__session`, which is also the one cookie Firebase Hosting
  forwards. The CSRF token is a separate readable cookie, double-submitted in forms and in the `X-XSRF-TOKEN` header.
- Rate limits per caller: five login attempts per quarter hour per address, three payment attempts per hour per
  session, sixty requests per minute in general. Five failed logins lock the agreement for thirty minutes.
- Bank account numbers and sort codes are encrypted at rest; only the last four and last two digits are stored in the
  clear and ever shown.
- Every sensitive operation (payments, contact, bank detail and payment date changes, statement requests) is written to
  the audit trail with the client address and a hash of the session id, never the id itself.
- Agreements are checked for ownership on every access; another customer's agreement answers 403, a missing one 404.

---

## Measurements

Taken on an arm64 Docker Desktop with the containerd image store, against the emulator, with the 78 shell checks and
4,000 further requests from `scripts/docker-test.sh`. The SIZE column of `docker images` on that store adds compressed
and unpacked content together, so the two underlying figures are given.

| Measure                                     | Debian slim with gRPC | Alpine, trimmed |
|---------------------------------------------|-----------------------|-----------------|
| Image, unpacked layers                      | 322 MB                | 169 MB          |
| Image, compressed (what a registry pulls)   | 148 MB                | 98 MB           |
| RSS idle after startup                      | 117 MB                | 127 MB          |
| RSS after 78 HTTP and route checks          | 144 MB                | 138 MB          |
| RSS after 4,000 further requests            | 151 MB                | 142 MB          |

Layers: 8.8 MB Alpine, 63 MB runtime, 49 MB jars, 47 MB class-data archive. First `/login` 1.3 s after `docker run`,
`docker stop` 0.6 s, no `OutOfMemoryError` at a 32 MB heap. The JVM flags are
`-XX:+UseSerialGC -Xss512k -Xmx32m -Xms16m -XX:MaxMetaspaceSize=48m -XX:ReservedCodeCacheSize=20m
-XX:TieredStopAtLevel=1 -XX:CICompilerCount=1`, with the class-data archive. Cloud Run allocates 512 MiB because its
gen2 execution environment accepts no less; the application does not need it.

---

## Known Gaps

- Document download is not implemented; the list links nowhere, as in the Spring app.
- `dependency-check` is configured but needs an NVD API key and has not been run.
- There is no Vite dev server integration; frontend changes need a rebuild and a restart.
