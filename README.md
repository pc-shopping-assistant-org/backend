# PC Shopping Backend

The backend for **PC Shopping Assistant** is a Spring Boot and Spring Cloud microservices system for authentication, product catalog, orders, payments, promotions, search, and media management.

The project is organized as a single Maven reactor. Services run on the host during local development, while infrastructure dependencies run in Docker Compose.

## Quick Start

From the repository root:

```powershell
docker compose up -d
mvn clean install -DskipTests
```

### AI database (local development)

The same PostgreSQL container initializes `ai_db` alongside the business-service
databases. No extra PostgreSQL container or test database is needed. For an
existing volume, init scripts do not run again automatically; create only the
missing AI database without resetting any data:

```sh
docker compose up -d postgres
docker compose exec -T -e POSTGRES_MULTIPLE_DATABASES=ai_db postgres \
  bash /docker-entrypoint-initdb.d/init-multiple-databases.sh
```

Root `../ai-service` connects on `127.0.0.1:5432/ai_db`. Current compose dev
credentials are `postgres` / `postgres`; use actual credentials if customized.
Do not run another PostgreSQL compose project on the same port or delete volumes
to add this database. AI migrations/checkpointer bootstrap are separate P2 work.

Create and load the local environment file before starting `identity-service`:

```powershell
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
}
Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    $name = $Matches[1].Trim()
    $value = $Matches[2].Trim()
    Set-Item -Path "Env:$name" -Value $value
}
```

Start the applications in this order, using a separate terminal for each process:

1. `discovery-server`
2. Business services
3. `api-gateway`

See [Running the Services](#running-the-services) for the exact commands.

## Architecture

### Python AI service

`ai-service/` is a Git submodule of `git@github.com:pc-shopping-assistant-org/ai-service.git`,
not a vendored source copy. It contains FastAPI/LangGraph/Pydantic and builds
independently of the Java Maven reactor. Catalog
retrieval reads the catalog-service public API; it does not access service databases.

```sh
git submodule update --init --recursive
```

Compose `build: ./ai-service` now uses this pinned submodule checkout. The sibling
root `../ai-service` repository and this checkout do not share uncommitted work;
commit/push AI changes first, then update the backend gitlink. Never publish a
backend gitlink to an AI commit unavailable on GitHub. The pinned checkout includes
the committed P0/P1 core/contracts and microservice catalog adapter. These local
AI commits still need publication before sharing the backend commit.

The prior vendored tree is preserved locally at
`../.ai-submodule-backup.WkkAdO/ai-service` (outside this Git repository), and is
also recoverable from backend Git history. Its `mainImageUrl` catalog-card mapping
and microservice-specific regression are now committed and included in this pin;
remote availability remains unverified (ISSUE-083).
do not reintroduce the old PydanticAI runtime. Compose already explicitly supplies
the microservice catalog URL; configure that URL when running AI directly on host.

Run on the host:

```bash
cd ai-service
uv sync --frozen
uv run uvicorn ai_service.main:app --host 127.0.0.1 --port 8000
```

Alternatively, from this backend directory:

```bash
docker compose --profile ai up -d --build ai-service
```

Start catalog-service on port 8082 and the gateway on port 8080. Gateway routes
`/api/v1/assistant/chat` and `/api/v1/assistant/chat/stream` to the AI service's
`/api/v1/chat` and `/api/v1/chat/stream`. Existing gateway JWT authentication
applies to these routes. Set `AI_SERVICE_URL` on the gateway if the AI host changes.
Swagger is available locally at `http://localhost:8000/docs`.

```bash
curl -N http://localhost:8080/api/v1/assistant/chat/stream \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Tìm laptop chơi game"}'
```

The default `AI_PROVIDER=fallback` produces deterministic catalog-grounded answers.
Configure `AI_PROVIDER=openai` or `gemini` and the corresponding API key to enable
model generation; use `ai-service/.env.example` for host execution. AI responses
retain `{data, message, errors}` with static message keys, including SSE frames.

Current integration limits: conversation storage is process-local and has no
per-account ownership binding; keep this local integration until ownership is
implemented before exposing persistent conversations to multiple users. The AI
service currently has no OTLP exporter, and Gateway SSE forwarding still requires
an end-to-end runtime check. Search-service indexing remains outside this integration.

```text
                         +----------------+
                         |  API Gateway   |
                         |     :8080      |
                         +--------+-------+
                                  |
              +-------------------+-------------------+
              |                   |                   |
        Identity :8081      Catalog :8082       Order :8083
        Payment :8084       Promotion :8085     Search :8086
        Media :8087
              |                   |                   |
              +-------------------+-------------------+
                                  |
                         +--------+-------+
                         | Eureka :8761  |
                         +----------------+

 PostgreSQL | Redis | RabbitMQ | Kafka | Zipkin | OpenTelemetry
```

### Application modules

| Module              | Responsibility                                      |
| ------------------- | --------------------------------------------------- |
| `discovery-server`  | Eureka service discovery                            |
| `api-gateway`       | Request routing and service entry point             |
| `common-lib`        | Shared backend infrastructure and response handling |
| `identity-service`  | Registration, authentication, JWT, and email OTP    |
| `catalog-service`   | Product catalog                                     |
| `order-service`     | Orders and order events                             |
| `payment-service`   | Payment processing                                  |
| `promotion-service` | Promotions                                          |
| `search-service`    | Product search                                      |
| `media-service`     | Media management                                    |

Business services register with Eureka. External clients should normally call them through the API Gateway rather than directly.

## Requirements

- Java `25`
- Maven `3.9+`
- Docker Desktop with Docker Compose support
- The following ports must be available: `5432`, `5672`, `6379`, `8761`, `8080-8087`, `15672`, `29092`, `9411`, `4317-4318`

Check Java and Maven in PowerShell:

```powershell
java -version
mvn -version
```

## Local Development

Run the following commands from the backend directory containing this `pom.xml` file.

### 1. Start Infrastructure

```powershell
docker compose up -d
```

The following containers are started:

| Component               | Address                            | Purpose                       |
| ----------------------- | ---------------------------------- | ----------------------------- |
| PostgreSQL              | `localhost:5432`                   | Database for the services     |
| RabbitMQ                | `localhost:5672`                   | AMQP messaging                |
| RabbitMQ Management     | http://localhost:15672             | Management UI, `guest/guest`  |
| Redis                   | `localhost:6379`                   | Cache and identity data       |
| Kafka                   | `localhost:29092`                  | Event streaming               |
| Zipkin                  | http://localhost:9411              | Distributed tracing           |
| OpenTelemetry Collector | `localhost:4317`, `localhost:4318` | Receives traces from services |

Check container status:

```powershell
docker compose ps
```

PostgreSQL automatically creates these databases: `identity_db`, `catalog_db`, `order_db`, `payment_db`, `promotion_db`, `search_db`, and `media_db`.

### 2. Configure Environment Variables

`identity-service` requires SMTP settings to send OTP emails. The repository ignores `.env`, so create it locally from `.env.example` and replace the placeholder values. Never commit real credentials.

Spring Boot does not load `.env` automatically when started with Maven. Load the file into the current PowerShell session before running `mvn spring-boot:run`:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*([^#][^=]*)=(.*)$' } | ForEach-Object {
    $name = $Matches[1].Trim()
    $value = $Matches[2].Trim()
    Set-Item -Path "Env:$name" -Value $value
}
```

Keep this PowerShell window open while starting the services. The other services use the local defaults in `src/main/resources/application.yaml`.

### 3. Build the Project

Build and install the modules into the local Maven repository so that services can run independently:

```powershell
mvn clean install -DskipTests
```

Run tests:

```powershell
mvn test
```

## Running the Services

Open a separate terminal for each service. Start them in the following order:

```powershell
mvn -pl discovery-server spring-boot:run
```

After Eureka is ready, start the business services:

```powershell
mvn -pl identity-service spring-boot:run
mvn -pl catalog-service spring-boot:run
mvn -pl order-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl promotion-service spring-boot:run
mvn -pl search-service spring-boot:run
mvn -pl media-service spring-boot:run
```

Finally, start the gateway:

```powershell
mvn -pl api-gateway spring-boot:run
```

The services require PostgreSQL and Eureka at startup. `catalog-service` and `order-service` also use Kafka; `order-service` uses RabbitMQ; and `identity-service` uses Redis and SMTP.

## Common Commands

| Command                             | Description                               |
| ----------------------------------- | ----------------------------------------- |
| `docker compose up -d`              | Start local infrastructure                |
| `docker compose ps`                 | Show infrastructure status                |
| `mvn clean install -DskipTests`     | Build and install all modules             |
| `mvn test`                          | Run the test suite                        |
| `mvn -pl <service> spring-boot:run` | Run one service                           |
| `docker compose down`               | Stop infrastructure and preserve volumes  |
| `docker compose down -v`            | Stop infrastructure and delete local data |

## Application Ports

| Application |   Port | Health or UI URL                      |
| ----------- | -----: | ------------------------------------- |
| Eureka      | `8761` | http://localhost:8761                 |
| API Gateway | `8080` | http://localhost:8080/actuator/health |
| Identity    | `8081` | http://localhost:8081/actuator/health |
| Catalog     | `8082` | http://localhost:8082/actuator/health |
| Order       | `8083` | http://localhost:8083/actuator/health |
| Payment     | `8084` | http://localhost:8084/actuator/health |
| Promotion   | `8085` | http://localhost:8085/actuator/health |
| Search      | `8086` | http://localhost:8086/actuator/health |
| Media       | `8087` | http://localhost:8087/actuator/health |

## Calling APIs Through the Gateway

The gateway supports concise resource-based paths for the current public controllers:

```text
http://localhost:8080/auth/**
http://localhost:8080/product-variants/**
http://localhost:8080/orders/**
http://localhost:8080/payments/**
```

For example, an endpoint mapped to `/auth/login` in `identity-service` is called through:

```text
http://localhost:8080/auth/login
```

The original service-prefixed paths remain available for backward compatibility and for services or endpoints that do not yet have a unique resource path:

```text
http://localhost:8080/identity-service/**
http://localhost:8080/catalog-service/**
http://localhost:8080/order-service/**
http://localhost:8080/payment-service/**
http://localhost:8080/promotion-service/**
http://localhost:8080/search-service/**
http://localhost:8080/media-service/**
```

The gateway forwards concise paths without removing a prefix. Therefore, a controller mapped to `/auth` in `identity-service` receives the same `/auth` path after routing.

For example, the current identity controller is mapped to `/auth`, so login is called through:

```text
http://localhost:8080/auth/login
```

## Stop the Environment

Stop the Spring Boot services with `Ctrl+C`, then stop the containers:

```powershell
docker compose down
```

Remove local database, message broker, and Redis data as well:

```powershell
docker compose down -v
```

Use `down -v` only when you want to reset all development data.

## Troubleshooting

### A service cannot connect to PostgreSQL, Redis, or a broker

Check that the infrastructure is running and healthy:

```powershell
docker compose ps
```

If a container was created before the current database list was configured, reset the local volumes and start again:

```powershell
docker compose down -v
docker compose up -d
```

### A service cannot register with Eureka

Start `discovery-server` first and verify http://localhost:8761 before starting the other services.

### OTP emails are not sent

Verify that `MAIL_USERNAME` and `MAIL_PASSWORD` were loaded into the same PowerShell session used to start `identity-service`. Gmail accounts generally require an app password rather than the normal account password. Identity now signs access and refresh tokens with RS256 and publishes its public key at `/.well-known/jwks.json`; local development generates and persists a key pair under `identity-service/.local`. For non-local deployments, set `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` to Base64-encoded PKCS#8 and X.509 key bytes and set `JWT_ALLOW_DEV_KEY_GENERATION=false`. Set `JWT_JWKS_URI` and `JWT_SECURITY_ISSUER` consistently on the gateway and resource services.

### Port already in use

Stop the process using the port or update the corresponding `server.port` and dependent configuration in that service's `src/main/resources/application.yaml`.

## Security Notes

- Use development-only credentials for local infrastructure.
- Keep `.env` out of commits and do not share real SMTP credentials or JWT signing keys.
- Rotate any credential that is accidentally exposed.

## Directory Structure

```text
backend/
├── api-gateway/
├── catalog-service/
├── common-lib/
├── discovery-server/
├── identity-service/
├── media-service/
├── order-service/
├── payment-service/
├── promotion-service/
├── search-service/
├── docker/
├── docker-compose.yml
└── pom.xml
```
