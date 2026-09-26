# Architecture: ocr-processor

> **Status:** 🟢 Approved  
> **Phase:** 2 of 6  
> **Last Updated:** 2025-07-14

---

## Overview

`ocr-processor` is a monolithic Spring Boot REST API with a server-rendered Thymeleaf frontend. Authenticated users can register, log in, upload documents for OCR extraction, search extracted content, and receive LLM-generated summaries — all stored in PostgreSQL.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Browser (Client)                     │
│          Thymeleaf-rendered HTML + minimal JS               │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTPS
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot Application                   │
│                                                             │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │  Security   │  │  Controller  │  │  Global Exception │  │
│  │  Filter     │→ │  Layer       │→ │  Handler          │  │
│  │  (JWT)      │  │  (REST +     │  │                   │  │
│  └─────────────┘  │   Thymeleaf) │  └───────────────────┘  │
│                   └──────┬───────┘                         │
│                          ▼                                  │
│                   ┌──────────────┐                          │
│                   │  Service     │                          │
│                   │  Layer       │                          │
│                   └──┬───┬───┬───┘                          │
│                      │   │   │                              │
│          ┌───────────┘   │   └────────────────┐            │
│          ▼               ▼                    ▼            │
│  ┌───────────────┐ ┌──────────┐  ┌─────────────────────┐  │
│  │ Repository    │ │ OCR      │  │ LLM Client          │  │
│  │ Layer (JPA)   │ │ Engine   │  │ (OpenAI/watsonx)    │  │
│  └───────┬───────┘ │(Tesseract│  └──────────┬──────────┘  │
│          │         │ via Tika)│             │ External API │
│          ▼         └──────────┘             │             │
│  ┌───────────────┐                          │             │
│  │  PostgreSQL   │◄─────────────────────────┘             │
│  └───────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Layers

### 1. Security Filter (Spring Security + JWT)
- Intercepts every request before it reaches a controller
- Validates JWT from HTTP-only cookie
- Populates `SecurityContext` with authenticated user
- Passes `/login`, `/register`, and static assets without auth

### 2. Controller Layer
- **AuthController** — `/login`, `/logout`, `/register`, `/profile/change-password`
- **HomeController** — `/home` (serves Search + Create tabs via Thymeleaf)
- **OcrController** — `/api/v1/ocr/**` (upload, extract, search, detail, retry-summary)

### 3. Service Layer
- **UserService** — registration, login, password change, account lockout
- **OcrService** — upload handling, file validation, status lifecycle management
- **TesseractOcrEngine** — delegates to Apache Tika + Tesseract for text extraction
- **LlmService** — calls configured LLM provider, handles truncation and retries

### 4. Repository Layer (Spring Data JPA)
- **UserRepository** — CRUD + username/email uniqueness queries
- **OcrRecordRepository** — full-text search, pagination, user-scoped queries

### 5. Database — PostgreSQL 16
- `users` table — account data, lockout tracking
- `ocr_records` table — document metadata, extracted text, LLM summary
- Full-text search via PostgreSQL `tsvector` / `GIN` index on `extracted_text` + `summary`

---

## Technology Stack

| Layer | Technology | Version | Rationale |
|---|---|---|---|
| Language | Java | 21 | Current LTS; virtual threads available |
| Framework | Spring Boot | 3.3.x | Industry standard; rich ecosystem |
| Build | Maven | 3.9.x | Widely adopted; good CI integration |
| Frontend | Thymeleaf | 3.1.x | Server-rendered; no separate SPA build pipeline needed for v1 |
| Security | Spring Security + JJWT | 0.12.x | Native Spring integration; JWT HTTP-only cookie |
| ORM | Spring Data JPA + Hibernate | 6.x | Standard JPA; query derivation |
| Database | PostgreSQL | 16 | Production-grade RDBMS; native full-text search |
| OCR | Apache Tika + Tesseract4J | 2.x / 1.x | Open-source; no per-call API cost |
| LLM | Spring AI (OpenAI adapter) | 1.0.x | Pluggable; swap provider via config |
| Testing (Java) | JUnit 5 + Mockito + Testcontainers | — | Standard Spring Boot test stack |
| Testing (Python) | pytest + requests | 8.x / 2.x | Lightweight E2E layer |
| CI | GitHub Actions | — | Native GitHub integration |
| Containerisation | Docker Compose (dev) | — | Local PostgreSQL + app |

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

## Security Model

| Concern | Approach |
|---|---|
| Authentication | JWT stored in `HttpOnly; Secure; SameSite=Strict` cookie |
| Password storage | bcrypt, cost factor 12 |
| Account lockout | 5 failed attempts → 15-minute lock (tracked in DB) |
| Authorisation | All OCR data queries filtered by `userId` from JWT |
| API key storage | Environment variables only (`LLM_API_KEY`); never in code or config files committed to repo |
| Input validation | Jakarta Bean Validation on all DTOs; parameterised JPA queries (no raw SQL) |
| CSRF | Disabled for REST endpoints; Thymeleaf form CSRF tokens enabled for HTML forms |

---

## Deployment (v1 — Local / Single Server)

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
| [ADR-001](adr/ADR-001-frontend-thymeleaf.md) | Use Thymeleaf (server-rendered) over SPA for v1 | Accepted |
| [ADR-002](adr/ADR-002-ocr-tesseract.md) | Use Tesseract + Apache Tika over cloud OCR API | Accepted |
| [ADR-003](adr/ADR-003-llm-spring-ai.md) | Use Spring AI with OpenAI adapter for LLM integration | Accepted |
| [ADR-004](adr/ADR-004-auth-jwt-cookie.md) | Store JWT in HTTP-only cookie, not Authorization header | Accepted |
| [ADR-005](adr/ADR-005-search-postgres-fts.md) | Use PostgreSQL full-text search over Elasticsearch | Accepted |
