---
title: Ocr Processor
emoji: 📄
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
license: mit
short_description: Document OCR, AI summaries & in-app test console
---

# ocr-processor

Spring Boot REST API + Thymeleaf web application for OCR text extraction and LLM-powered summarisation (supporting DeepSeek and OpenAI via Spring AI). Features an interactive, in-app **Test Console** with 101 automated test cases, live execution evidence cards, and requirements-phase coverage traceability.

## Project Phases

| Phase | Status | Document |
|---|---|---|
| 1. Requirements | ✅ Complete | [`docs/requirements/REQUIREMENTS.md`](docs/requirements/REQUIREMENTS.md) (52 acceptance criteria across 7 stories) |
| 2. Architecture & ADRs | ✅ Complete | [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) (5 Architecture Decision Records) |
| 3. Design | ✅ Complete | [`docs/design/DESIGN.md`](docs/design/DESIGN.md) + [`docs/design/TEST_CONSOLE.md`](docs/design/TEST_CONSOLE.md) |
| 4. Implementation + Unit Tests | ✅ Complete | `src/main/` + `src/test/unit/` (32 unit test cases) |
| 5. Regression + E2E Tests | ✅ Complete | `src/test/integration/` (29 cases) + `tests/` (40 E2E cases) |
| 6. Documentation | ✅ Complete | This README + `docs/` + Executive Presentation Decks |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.3 |
| Build | Maven 3.9 |
| Frontend | Thymeleaf 3 (server-rendered templates) + Vanilla CSS/JS |
| Security | Spring Security + JJWT 0.12 (HTTP-only cookie) |
| Database | PostgreSQL 16 + Flyway migrations + TSVECTOR GIN full-text search |
| OCR | Apache Tika 2 + Tesseract CLI (embedded execution) |
| LLM | Spring AI 1.0 (DeepSeek and OpenAI compatible via `ChatClient`) |
| Unit Tests | JUnit 5 + Mockito (32 cases) |
| Integration Tests | Spring Boot Test + Testcontainers PostgreSQL (29 cases) |
| E2E / UI Tests | Python pytest + Selenium WebDriver Chrome Headless (40 cases) |
| CI | GitHub Actions |

---

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 16 (or Docker)
- Tesseract OCR binary: `sudo apt-get install tesseract-ocr` (Linux) / `brew install tesseract` (macOS)
- Python 3.12+ (for E2E and Selenium browser suites)

---

## Quick Start

### 1. Clone and configure environment

```bash
git clone https://github.com/veerupaddu/ocr-processor.git
cd ocr-processor
cp .env.example .env   # edit with your values
```

Required environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ocrprocessor
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
JWT_SECRET=your-secret-key-must-be-at-least-32-characters
LLM_API_KEY=your-api-key    # OpenAI or DeepSeek API key
LLM_BASE_URL=https://api.deepseek.com   # Optional: set for DeepSeek
LLM_MODEL=deepseek-chat                 # Optional: defaults to gpt-4o-mini
```

### 2. Create the database

```bash
createdb ocrprocessor
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The application starts at **http://localhost:8080**

Flyway automatically executes database migrations upon application startup.

### 4. Using Docker Compose (recommended for local dev)

```bash
docker-compose up
```

---

## Running Tests

### Java unit + integration tests via Maven

```bash
mvn test
```

Testcontainers manages an isolated PostgreSQL container automatically for integration testing.

### Python E2E & Selenium tests (requires running app)

```bash
cd tests
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# E2E & Selenium journeys
pytest e2e/ --base-url=http://localhost:8080 -v

# Regression suite
pytest regression/ --base-url=http://localhost:8080 -v
```

### Interactive Test Console (In-App)

Navigate to **http://localhost:8080/home#tests** after logging in:
- Execute Unit (32 cases), Regression (29 cases), or End-to-end (40 cases) suites with live progress streaming.
- Inspect triple-fold evidence (`Executed` / `Validated` / `Observed`) and full-page Selenium screenshots.
- Explore the **Coverage** sub-tab tracking 38 of 52 covered acceptance criteria (73.1% reach).

---

## Application Features

| Feature | URL | Description |
|---|---|---|
| Register | `/register` | Create a new user account |
| Login | `/login` | Authenticate with username and password |
| Home | `/home` | Primary workspace with Search, Create, and Tests tabs |
| Change Password | `/profile/change-password` | Update account credentials |
| Upload & Extract | `POST /api/v1/ocr/upload` | Upload PDF/image for synchronous OCR text extraction |
| Search | `GET /api/v1/ocr/search` | Fast TSVECTOR full-text search across user records |
| Detail | `GET /api/v1/ocr/{id}` | View full extracted text, document metadata, and AI summary |
| Retry Summary | `POST /api/v1/ocr/{id}/retry-summary` | Re-trigger LLM summarisation for failed inference |
| Test Console | `/home#tests` | In-app test runner (101 cases), execution evidence, and requirement coverage |

---

## Project Structure

```
ocr-processor/
├── docs/
│   ├── requirements/REQUIREMENTS.md   Phase 1: 52 acceptance criteria across 7 stories
│   ├── architecture/
│   │   ├── ARCHITECTURE.md            Phase 2: System design & layers
│   │   ├── TECH_SPEC.md               Phase 2: API contracts & DB schema
│   │   └── adr/                       5 Architecture Decision Records
│   └── design/
│       ├── DESIGN.md                  Phase 3: Wireframes & sequence diagrams
│       └── TEST_CONSOLE.md           Test Console design and evidence cards
│
├── src/
│   ├── main/java/ai/medhaleak/ocrprocessor/
│   │   ├── config/          SecurityConfig, JwtUtil, JwtAuthFilter, AppProperties
│   │   ├── controller/      AuthController, HomeController, OcrController, TestConsoleController
│   │   ├── service/         UserService, OcrService, TesseractOcrEngine, SpringAiLlmService, TestConsoleService
│   │   ├── repository/      UserRepository, OcrRecordRepository (PostgreSQL FTS)
│   │   ├── model/           User, OcrRecord
│   │   ├── dto/             RegisterRequest, LoginRequest, OcrRecordDto, …
│   │   ├── testing/         EvidenceCard, FailureExplanation, JunitXmlReport
│   │   └── exception/       Typed exceptions + GlobalExceptionHandler
│   ├── main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/V1__init_schema.sql
│   │   ├── templates/       Thymeleaf HTML (auth, home, ocr)
│   │   ├── static/          CSS + JS (tabs.js, upload.js, tests.js)
│   │   └── test-catalog.json Complete 101-case test catalog
│   └── test/
│       ├── unit/service/    UserServiceTest, OcrServiceTest, LlmServiceTest, JwtUtilTest
│       ├── unit/testing/    FailureSamplesTest, TestConsoleCatalogTest
│       └── integration/     AuthControllerIT, OcrControllerIT, OcrRecordRepositoryIT, ApplicationContextIT
│
└── tests/
    ├── e2e/                 pytest: test_auth, test_ocr, test_search, test_summary
    ├── e2e/selenium/        Selenium WebDriver journeys with Chrome Headless
    ├── regression/          pytest: test_regression
    └── requirements.txt
```

---

## Configuration Reference

All settings reside in [`src/main/resources/application.yml`](src/main/resources/application.yml). Key properties:

| Property | Default | Description |
|---|---|---|
| `app.jwt.expiry-hours` | `8` | JWT token lifetime in hours |
| `app.ocr.max-file-size-bytes` | `20971520` | Max upload size (20 MB) |
| `app.ocr.allowed-types` | PDF, PNG, JPG, TIFF | Accepted MIME types |
| `app.llm.max-input-chars` | `10000` | Max chars sent to LLM |
| `app.llm.prompt-template` | (summarise prompt) | LLM prompt; use `{extractedText}` placeholder |
| `app.security.max-login-attempts` | `5` | Attempts before account lockout |
| `app.security.lockout-duration-minutes` | `15` | Lockout duration |
| `spring.ai.openai.chat.options.model` | `gpt-4o-mini` | LLM model identifier |

---

## Changing the LLM Provider (DeepSeek / OpenAI / Ollama)

Spring AI abstracts LLM calls through `ChatClient`. DeepSeek and OpenAI share the OpenAI chat protocol:

1. **For DeepSeek:**
   ```bash
   export LLM_API_KEY="sk-your-deepseek-key"
   export LLM_BASE_URL="https://api.deepseek.com"
   export LLM_MODEL="deepseek-chat"
   ```

2. **For OpenAI:**
   ```bash
   export LLM_API_KEY="sk-your-openai-key"
   export LLM_BASE_URL="https://api.openai.com"
   export LLM_MODEL="gpt-4o-mini"
   ```

3. **For Local Ollama or Other Providers:**
   Update the dependency in `pom.xml` and properties in `application.yml`. No Java code changes are needed.

---

## Security Model

- Passwords are encrypted with **bcrypt** (cost factor 12)
- JWT is stored in an **HTTP-only, SameSite=Strict** cookie; JavaScript access is restricted
- Accounts are locked for **15 minutes** after 5 consecutive failed login attempts
- All OCR data queries are strictly scoped to the **authenticated user**
- API keys and secrets are injected via **environment variables**
