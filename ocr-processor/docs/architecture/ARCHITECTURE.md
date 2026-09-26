# Architecture: ocr-processor

> **Status:** 🟢 Approved / Implemented  
> **Phase:** 2 of 6  
> **Last Updated:** 2026-09-27

---

## Overview

`ocr-processor` is a monolithic Spring Boot REST API with a server-rendered Thymeleaf frontend. Authenticated users can register, log in, upload documents for OCR extraction, search extracted content, and receive LLM-generated summaries (powered by DeepSeek or OpenAI via Spring AI) — all stored in PostgreSQL. The system integrates an interactive, in-app **Test Console** allowing developers and QA engineers to trigger unit, integration, and end-to-end suites directly from the browser with live execution evidence and functional requirement coverage.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            Browser (Client)                             │
│       Thymeleaf HTML + CSS + JS (Search, Create, Tests Tabs)            │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │ HTTPS / JWT Cookie
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Spring Boot Application                         │
│                                                                         │
│  ┌─────────────┐  ┌───────────────────────────────┐ ┌────────────────┐  │
│  │  Security   │  │       Controller Layer        │ │ Global Exception│ │
│  │  Filter     │→ │  • AuthController             │ │ Handler        │  │
│  │  (JWT)      │  │  • HomeController             │ └────────────────┘  │
│  └─────────────┘  │  • OcrController              │                     │
│                   │  • TestConsoleController      │                     │
│                   └───────────────┬───────────────┘                     │
│                                   ▼                                     │
│                   ┌───────────────────────────────┐                     │
│                   │         Service Layer         │                     │
│                   │  • UserService                │                     │
│                   │  • OcrService                 │                     │
│                   │  • TesseractOcrEngine         │                     │
│                   │  • SpringAiLlmService         │                     │
│                   │  • TestConsoleService         │                     │
│                   └───┬───────┬───────┬───────┬───┘                     │
│                       │       │       │       │                         │
│           ┌───────────┘       │       │       └───────────┐             │
│           ▼                   ▼       ▼                   ▼             │
│  ┌────────────────┐ ┌──────────┐ ┌─────────────┐ ┌───────────────────┐  │
│  │ Repository     │ │ Tesseract│ │ LLM Client  │ │ Test Process      │  │
│  │ Layer (JPA)    │ │ Engine   │ │ (DeepSeek / │ │ Orchestrator      │  │
│  └────────┬───────┘ │(CLI via  │ │  OpenAI)    │ │ (Maven + Pytest + │  │
│           │         │ Tika)    │ └──────┬──────┘ │  Selenium Headless│  │
│           ▼         └──────────┘        │        └───────────────────┘  │
│  ┌────────────────┐                     ▼                               │
│  │   PostgreSQL   │◄────────────────────┘                               │
│  │ (FTS TSVECTOR) │                                                     │
│  └────────────────┘                                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Layers

### 1. Security Filter (Spring Security + JWT)
- Intercepts every request before it reaches a controller
- Validates JWT from HTTP-only cookie
- Populates `SecurityContext` with authenticated user identity
- Passes `/login`, `/register`, and static assets without auth

### 2. Controller Layer
- **AuthController** — `/login`, `/logout`, `/register`, `/profile/change-password`
- **HomeController** — `/home` (serves Search, Create, and Tests tabs via Thymeleaf)
- **OcrController** — `/api/v1/ocr/**` (upload, extract, search, detail, retry-summary)
- **TestConsoleController** — `/api/v1/tests/**` (suite execution, status queries, screenshot delivery)

### 3. Service Layer
- **UserService** — registration, login, password change, account lockout
- **OcrService** — upload handling, file validation, status lifecycle management
- **TesseractOcrEngine** — delegates to Apache Tika + Tesseract for text extraction
- **SpringAiLlmService** — calls configured LLM provider (DeepSeek / OpenAI), handles prompt formatting, truncation bounds, and retries
- **TestConsoleService** — parses `test-catalog.json`, manages asynchronous background suite runs, executes Maven Surefire / Pytest processes, extracts triple-fold evidence, and formats root-cause diagnostics

### 4. Repository Layer (Spring Data JPA)
- **UserRepository** — CRUD + username/email uniqueness queries
- **OcrRecordRepository** — full-text search, pagination, user-scoped queries

### 5. Database — PostgreSQL 16
- `users` table — account data, lockout tracking
- `ocr_records` table — document metadata, extracted text, LLM summary
- Full-text search via PostgreSQL `tsvector` / `GIN` index on `document_name`, `extracted_text`, and `summary`

---

## Technology Stack

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| Language | Java | 21 | Current LTS; virtual threads available |
| Framework | Spring Boot | 3.3.x | Industry standard; rich ecosystem |
| Build | Maven | 3.9.x | Widely adopted; good CI integration |
| Frontend | Thymeleaf | 3.1.x | Server-rendered templates; unified deployment package |
| Security | Spring Security + JJWT | 0.12.x | Native Spring integration; JWT HTTP-only cookie |
| ORM | Spring Data JPA + Hibernate | 6.x | Standard JPA; query derivation |
| Database | PostgreSQL | 16 | Production-grade RDBMS; native TSVECTOR full-text search with GIN indexing |
| OCR | Apache Tika + Tesseract CLI | 2.x / 5.x | Open-source; zero per-call execution cost |
| LLM | Spring AI (OpenAI & DeepSeek compatible) | 1.0.x | Pluggable ChatClient; provider configurable via properties |
| Testing (Java) | JUnit 5 + Mockito + Testcontainers | 5.x / 1.19.x | Standard Spring Boot unit and integration test stack |
| Testing (E2E & UI) | Python pytest + Selenium WebDriver | 8.x / 4.x | Real browser journeys in Chrome headless with automatic screenshot capture |
| CI | GitHub Actions | — | Native GitHub automation for Java and Python suites |
| Containerisation | Docker Compose (dev) | — | Local PostgreSQL orchestration |

---

## Request Flow — OCR Upload

```
Browser
  │ POST /api/v1/ocr/upload (multipart)
  ▼
JwtAuthFilter → validates JWT cookie
  ▼
OcrController.upload()
  ▼
OcrService.processUpload()
  ├─ validate file type + size
  ├─ save OcrRecord (status=PROCESSING)
  ├─ TesseractOcrEngine.extract(file)   ← synchronous in v1
  ├─ update OcrRecord (status=COMPLETE, extractedText=...)
  ├─ LlmService.summarise(extractedText) ← synchronous in v1
  └─ update OcrRecord (summaryStatus=COMPLETE, summary=...)
  ▼
OcrController returns JSON {id, status, extractedText, summary}
  ▼
Browser renders preview panel
```

---

## Request Flow — Search

```
Browser
  │ GET /api/v1/ocr/search?q=invoice&page=0
  ▼
JwtAuthFilter → validates JWT cookie
  ▼
OcrController.search()
  ▼
OcrService.search(userId, query, pageable)
  ▼
OcrRecordRepository.searchByUser(userId, tsquery, pageable)
  ← PostgreSQL GIN full-text search on extracted_text + summary
  ▼
Returns Page<OcrRecordSummaryDto>
  ▼
Browser renders result list
```

---

## Request Flow — In-App Test Console Execution

```
Browser (Tests Tab)
  │ POST /api/v1/tests/{suite}/run  (optional body: {"cases": ["Id1", "Id2"]})
  ▼
JwtAuthFilter → validates JWT cookie
  ▼
TestConsoleController.runSuite(suite, request)
  ▼
TestConsoleService.triggerSuiteRun(suite, caseIds)
  ├─ validates no other suite is actively executing
  ├─ transitions suite status to RUNNING
  ├─ spawns background execution thread:
  │    ├─ Unit / Integration: runs 'mvn test' targeting selected test methods
  │    ├─ End-to-End: runs 'pytest' targeting selected Selenium & API node IDs
  │    ├─ parses Surefire / Pytest JUnit XML reports as cases complete
  │    ├─ extracts triple-fold evidence (Executed, Validated, Observed)
  │    └─ updates case state, duration, and error diagnostics in memory
  ▼
Controller immediately returns 202 Accepted with current test suite snapshot
  ▼
Browser polls GET /api/v1/tests every second and renders live results into the two-row table
```

---

## Security Model

| Concern | Approach |
|---|---|
| Authentication | JWT stored in `HttpOnly; Secure; SameSite=Strict` cookie |
| Password storage | bcrypt, cost factor 12 |
| Account lockout | 5 failed attempts triggers 15-minute lock (tracked in DB) |
| Authorisation | All OCR data queries filtered by `userId` from JWT |
| API key storage | Environment variables (`LLM_API_KEY`, `JWT_SECRET`) |
| Input validation | Jakarta Bean Validation on all DTOs; parameterised JPA queries |
| CSRF | Thymeleaf form CSRF tokens enabled for HTML forms; REST endpoints protected by SameSite cookie |

---

## Deployment (Local / Single Server)

```
docker-compose up
  ├─ postgres:16
  └─ ocr-processor:latest  (JAR inside Docker image)
```

Environment variables injected at runtime:
```
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
LLM_API_KEY
JWT_SECRET
```

---

## ADR Index

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-frontend-thymeleaf.md) | Use Thymeleaf server-rendered templates for web interface | Accepted |
| [ADR-002](adr/ADR-002-ocr-tesseract.md) | Use Tesseract 5 with Apache Tika for embedded OCR | Accepted |
| [ADR-003](adr/ADR-003-llm-spring-ai.md) | Use Spring AI with OpenAI and DeepSeek compatibility | Accepted |
| [ADR-004](adr/ADR-004-auth-jwt-cookie.md) | Store JWT in HTTP-only cookie | Accepted |
| [ADR-005](adr/ADR-005-search-postgres-fts.md) | Use PostgreSQL TSVECTOR full-text search with GIN index | Accepted |
