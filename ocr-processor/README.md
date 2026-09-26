# ocr-processor

Spring Boot REST API + Thymeleaf web application for OCR text extraction and LLM-powered summarisation.

## Project Phases

| Phase | Status | Document |
|---|---|---|
| 1. Requirements | ✅ Complete | [`docs/requirements/REQUIREMENTS.md`](docs/requirements/REQUIREMENTS.md) |
| 2. Architecture & ADRs | ✅ Complete | [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) |
| 3. Design | ✅ Complete | [`docs/design/DESIGN.md`](docs/design/DESIGN.md) |
| 4. Implementation + Unit Tests | ✅ Complete | `src/main/` + `src/test/unit/` |
| 5. Regression + E2E Tests | ✅ Complete | `src/test/integration/`, `tests/` |
| 6. Documentation | ✅ Complete | This README + `docs/` |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Maven 3.9 |
| Frontend | Thymeleaf 3 (server-rendered) |
| Security | Spring Security + JJWT 0.12 (HTTP-only cookie) |
| Database | PostgreSQL 16 + Flyway migrations |
| OCR | Apache Tika 2 + Tesseract4J (Tess4J) |
| LLM | Spring AI 1.0 (OpenAI GPT-4o-mini by default) |
| Unit Tests | JUnit 5 + Mockito |
| Integration Tests | Spring Boot Test + Testcontainers |
| E2E / Regression | Java REST Assured + Python pytest |
| CI | GitHub Actions |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 16 (or Docker)
- Tesseract OCR binary: `sudo apt-get install tesseract-ocr` (Linux) / `brew install tesseract` (macOS)
- Python 3.12+ (for E2E tests only)

---

## Quick Start

### 1. Clone and configure environment

```bash
git clone <repo-url>
cd ocr-processor
cp .env.example .env   # edit with your values
```

Required environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ocrprocessor
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
JWT_SECRET=your-secret-key-must-be-at-least-32-characters
LLM_API_KEY=sk-...          # OpenAI API key
```

### 2. Create the database

```bash
createdb ocrprocessor
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The app starts at **http://localhost:8080**

Flyway will automatically run the database migration on first start.

### 4. Using Docker Compose (recommended for local dev)

```bash
docker-compose up
```

---

## Running Tests

### Java unit + integration tests

```bash
mvn test
```

Testcontainers spins up a PostgreSQL container automatically — no local DB needed for tests.

### Python E2E tests (requires running app)

```bash
cd tests
pip install -r requirements.txt

# E2E
pytest e2e/ --base-url=http://localhost:8080 -v

# Regression
pytest regression/ --base-url=http://localhost:8080 -v
```

---

## Application Features

| Feature | URL | Description |
|---|---|---|
| Register | `/register` | Create a new account |
| Login | `/login` | Log in with username + password |
| Home | `/home` | Search and Create tabs |
| Change Password | `/profile/change-password` | Update your password |
| Upload & Extract | `POST /api/v1/ocr/upload` | Upload PDF/image for OCR |
| Search | `GET /api/v1/ocr/search` | Full-text search across your records |
| Detail | `GET /api/v1/ocr/{id}` | View full OCR result + LLM summary |
| Retry Summary | `POST /api/v1/ocr/{id}/retry-summary` | Re-run LLM summarisation |
| Test Console | `/home#tests` | Interactive test runner (101 cases), execution evidence, and requirement coverage |

---

## Project Structure

```
ocr-processor/
├── docs/
│   ├── requirements/REQUIREMENTS.md   Phase 1: 47 acceptance criteria
│   ├── architecture/
│   │   ├── ARCHITECTURE.md            Phase 2: System design
│   │   ├── TECH_SPEC.md               Phase 2: API contracts + DB schema
│   │   └── adr/                       5 Architecture Decision Records
│   └── design/
│       ├── DESIGN.md                  Phase 3: Wireframes + sequence diagrams
│       └── TEST_CONSOLE.md           Test Console design and evidence cards
│
├── src/
│   ├── main/java/ai/medhaleak/ocrprocessor/
│   │   ├── config/          SecurityConfig, JwtUtil, JwtAuthFilter, AppProperties
│   │   ├── controller/      AuthController, HomeController, OcrController
│   │   ├── service/         UserService, OcrService, TesseractOcrEngine, SpringAiLlmService
│   │   ├── repository/      UserRepository, OcrRecordRepository (PostgreSQL FTS)
│   │   ├── model/           User, OcrRecord
│   │   ├── dto/             RegisterRequest, LoginRequest, OcrRecordDto, …
│   │   └── exception/       Typed exceptions + GlobalExceptionHandler
│   ├── main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/V1__init_schema.sql
│   │   ├── templates/       Thymeleaf HTML (auth, home, ocr)
│   │   └── static/          CSS + JS
│   └── test/
│       ├── unit/service/    UserServiceTest, OcrServiceTest, LlmServiceTest, JwtUtilTest
│       └── integration/     AuthControllerIT, OcrControllerIT, OcrRecordRepositoryIT
│
└── tests/
    ├── e2e/                 pytest: test_auth, test_ocr, test_search, test_summary
    ├── regression/          pytest: test_regression (18 critical-path checks)
    └── requirements.txt
```

---

## Configuration Reference

All settings live in [`src/main/resources/application.yml`](src/main/resources/application.yml). Key properties:

| Property | Default | Description |
|---|---|---|
| `app.jwt.expiry-hours` | `8` | JWT token lifetime in hours |
| `app.ocr.max-file-size-bytes` | `20971520` | Max upload size (20 MB) |
| `app.ocr.allowed-types` | PDF, PNG, JPG, TIFF | Accepted MIME types |
| `app.llm.max-input-chars` | `10000` | Max chars sent to LLM |
| `app.llm.prompt-template` | (summarise prompt) | LLM prompt; use `{extractedText}` placeholder |
| `app.security.max-login-attempts` | `5` | Attempts before account lockout |
| `app.security.lockout-duration-minutes` | `15` | Lockout duration |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | LLM model name |

---

## Changing the LLM Provider

Spring AI supports multiple providers. To switch from OpenAI to another:

1. Replace `spring-ai-openai-spring-boot-starter` in `pom.xml` with the desired adapter (e.g. `spring-ai-anthropic-spring-boot-starter`)
2. Update `spring.ai.*` config in `application.yml`
3. Set the appropriate API key environment variable

No Java code changes needed — `SpringAiLlmService` uses the `ChatClient` abstraction.

---

## Security Notes

- Passwords are hashed with **bcrypt** (cost factor 12)
- JWT is stored in an **HTTP-only, SameSite=Strict** cookie — not accessible to JavaScript
- Accounts are locked for **15 minutes** after 5 consecutive failed login attempts
- All OCR data queries are scoped to the **authenticated user** — cross-user access returns 404
- API keys and secrets are injected via **environment variables** — never committed to source
