# Technical Specification: ocr-processor

> **Status:** 🟢 Approved  
> **Phase:** 2 of 6  
> **Last Updated:** 2025-07-14

---

## 1. Overview

This document specifies the technical interfaces, API contracts, data schemas, and component behaviour for `ocr-processor`. It is the authoritative reference for implementation (Phase 4).

---

## 2. API Specification

### Base URL
```
http://localhost:8080
```

### Authentication
All endpoints except those marked **Public** require a valid JWT in an HTTP-only cookie (`jwt`).

---

### 2.1 Auth Endpoints

#### `POST /register` — Public
Register a new user.

**Request** (`application/x-www-form-urlencoded` or JSON):
```json
{
  "username": "string (3–50 chars, alphanumeric + underscore)",
  "email":    "string (valid email, max 254 chars)",
  "password": "string (8–128 chars)",
  "confirmPassword": "string"
}
```

**Responses:**
| Status | Body | Condition |
|---|---|---|
| `302 Found` | — | Redirect to `/login` on success |
| `400 Bad Request` | `{ "errors": { "field": "message" } }` | Validation failure |
| `409 Conflict` | `{ "error": "Username already taken" }` | Duplicate username |
| `409 Conflict` | `{ "error": "Email already registered" }` | Duplicate email |

---

#### `POST /login` — Public
Authenticate and receive JWT cookie.

**Request:**
```json
{
  "username": "string",
  "password": "string"
}
```

**Responses:**
| Status | Body | Condition |
|---|---|---|
| `302 Found` | — | Redirect to `/home`; sets `jwt` cookie |
| `401 Unauthorized` | `{ "error": "Invalid username or password" }` | Bad credentials |
| `423 Locked` | `{ "error": "Account temporarily locked. Try again later." }` | Lockout active |

---

#### `POST /logout` — Authenticated
Invalidate JWT cookie.

**Responses:**
| Status | Condition |
|---|---|
| `302 Found` | Clears `jwt` cookie; redirect to `/login` |

---

#### `POST /profile/change-password` — Authenticated
Change authenticated user's password.

**Request:**
```json
{
  "currentPassword": "string",
  "newPassword":     "string (8–128 chars)",
  "confirmPassword": "string"
}
```

**Responses:**
| Status | Body | Condition |
|---|---|---|
| `200 OK` | `{ "message": "Password changed successfully" }` | Success |
| `400 Bad Request` | `{ "errors": { "field": "message" } }` | Validation failure |
| `401 Unauthorized` | `{ "error": "Current password is incorrect" }` | Wrong current password |

---

### 2.2 Home / UI Endpoints

#### `GET /home` — Authenticated
Serves the Thymeleaf home page with Search and Create tabs.

**Responses:**
| Status | Condition |
|---|---|
| `200 OK` | HTML page |
| `302 Found` | Redirect to `/login` if unauthenticated |

---

### 2.3 OCR Endpoints

#### `POST /api/v1/ocr/upload` — Authenticated
Upload a document and trigger OCR + LLM summarisation.

**Request** (`multipart/form-data`):
| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | File | Yes | PDF, PNG, JPG, JPEG, TIFF; max 20 MB |
| `documentName` | String | No | Defaults to original filename |

**Response `201 Created`:**
```json
{
  "id":            "uuid",
  "documentName":  "string",
  "status":        "COMPLETE | FAILED",
  "summaryStatus": "COMPLETE | FAILED | SKIPPED",
  "extractedText": "string",
  "summary":       "string | null",
  "createdAt":     "ISO-8601"
}
```

**Error Responses:**
| Status | Condition |
|---|---|
| `400 Bad Request` | Missing file, unsupported type, or file too large |
| `422 Unprocessable Entity` | File accepted but OCR extraction failed |

---

#### `GET /api/v1/ocr/search` — Authenticated
Search OCR records owned by the authenticated user.

**Query Parameters:**
| Param | Type | Required | Default | Notes |
|---|---|---|---|---|
| `q` | String | No | `""` | Search term; empty = return all |
| `page` | Int | No | `0` | Zero-based page number |
| `size` | Int | No | `20` | Results per page (max 100) |

**Response `200 OK`:**
```json
{
  "content": [
    {
      "id":           "uuid",
      "documentName": "string",
      "status":       "COMPLETE",
      "preview":      "string (max 200 chars of extractedText)",
      "createdAt":    "ISO-8601"
    }
  ],
  "page":          0,
  "size":          20,
  "totalElements": 42,
  "totalPages":    3
}
```

---

#### `GET /api/v1/ocr/{id}` — Authenticated
Get full detail of a single OCR record (must be owned by the authenticated user).

**Response `200 OK`:**
```json
{
  "id":            "uuid",
  "documentName":  "string",
  "originalFilename": "string",
  "fileType":      "PDF",
  "fileSizeBytes": 102400,
  "status":        "COMPLETE",
  "summaryStatus": "COMPLETE",
  "extractedText": "string",
  "summary":       "string | null",
  "createdAt":     "ISO-8601",
  "updatedAt":     "ISO-8601"
}
```

**Error Responses:**
| Status | Condition |
|---|---|
| `404 Not Found` | Record does not exist or not owned by user |

---

#### `POST /api/v1/ocr/{id}/retry-summary` — Authenticated
Retry LLM summarisation for a record with `summaryStatus = FAILED`.

**Response `200 OK`:**
```json
{
  "id":            "uuid",
  "summaryStatus": "COMPLETE | FAILED",
  "summary":       "string | null"
}
```

**Error Responses:**
| Status | Condition |
|---|---|
| `404 Not Found` | Record not found or not owned by user |
| `409 Conflict` | `summaryStatus` is not `FAILED` |

---

## 3. Database Schema

```sql
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Full-text search extension (optional, for pg_trgm fuzzy search in v2)
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username              VARCHAR(50)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP    NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_username UNIQUE (LOWER(username)),
    CONSTRAINT uq_users_email    UNIQUE (LOWER(email))
);

CREATE TABLE ocr_records (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_name     VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type         VARCHAR(20)  NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    extracted_text    TEXT,
    summary           TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    summary_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    search_vector     TSVECTOR,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- GIN index for full-text search
CREATE INDEX idx_ocr_records_search ON ocr_records USING GIN (search_vector);

-- Index for user-scoped queries
CREATE INDEX idx_ocr_records_user_id ON ocr_records (user_id);

-- Trigger: maintain search_vector on insert/update
CREATE OR REPLACE FUNCTION ocr_records_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('english', coalesce(NEW.document_name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.extracted_text, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(NEW.summary, '')), 'C');
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ocr_records_search_vector_trigger
  BEFORE INSERT OR UPDATE ON ocr_records
  FOR EACH ROW EXECUTE FUNCTION ocr_records_search_vector_update();
```

---

## 4. Component Interfaces (Java)

### 4.1 OcrEngine
```java
public interface OcrEngine {
    /**
     * Extract text from the given file bytes.
     * @param fileBytes raw file content
     * @param mimeType  detected MIME type
     * @return extracted plain text (empty string if no text found)
     * @throws OcrExtractionException on irrecoverable extraction failure
     */
    String extract(byte[] fileBytes, String mimeType) throws OcrExtractionException;
}
```

### 4.2 LlmService
```java
public interface LlmService {
    /**
     * Generate a 3–5 sentence summary of the given text.
     * Truncates input to 10,000 characters before sending.
     * @param text extracted OCR text
     * @return summary string
     * @throws LlmUnavailableException when the LLM provider cannot be reached
     */
    String summarise(String text) throws LlmUnavailableException;
}
```

### 4.3 OcrService
```java
public interface OcrService {
    OcrRecordDto processUpload(UUID userId, MultipartFile file, String documentName);
    Page<OcrRecordSummaryDto> search(UUID userId, String query, Pageable pageable);
    OcrRecordDto getById(UUID userId, UUID recordId);
    OcrRecordDto retrySummary(UUID userId, UUID recordId);
}
```

### 4.4 UserService
```java
public interface UserService {
    void register(RegisterRequest request);
    AuthResult login(LoginRequest request);
    void changePassword(UUID userId, ChangePasswordRequest request);
}
```

---

## 5. Configuration (`application.yml` skeleton)

```yaml
spring:
  datasource:
    url:      ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/ocrprocessor}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate          # Flyway manages schema
    show-sql: false
  servlet:
    multipart:
      max-file-size:    20MB
      max-request-size: 21MB
  ai:
    openai:
      api-key: ${LLM_API_KEY}
      chat:
        options:
          model:       gpt-4o-mini
          temperature: 0.3

app:
  jwt:
    secret:     ${JWT_SECRET}
    expiry-hours: 8
  ocr:
    max-file-size-bytes: 20971520   # 20 MB
    allowed-types:
      - application/pdf
      - image/png
      - image/jpeg
      - image/tiff
  llm:
    max-input-chars: 10000
    prompt-template: >
      Summarise the following document text in 3–5 sentences.
      Be concise and factual.

      {extractedText}
  security:
    max-login-attempts: 5
    lockout-duration-minutes: 15
```

---

## 6. Error Handling Strategy

| Layer | Strategy |
|---|---|
| Controller | `@ControllerAdvice` + `@ExceptionHandler` maps domain exceptions to HTTP responses |
| Service | Throws typed exceptions (`OcrExtractionException`, `LlmUnavailableException`, `UserNotFoundException`, `DuplicateUsernameException`) |
| Repository | JPA exceptions wrapped in service layer |
| LLM | Retry once on timeout; catch all other errors and set `summaryStatus = FAILED` |
| OCR | Catch all `Throwable` from Tesseract/Tika; set `status = FAILED`; log full stack trace |

---

## 7. Testing Strategy

### Unit Tests (JUnit 5 + Mockito)
- `UserServiceTest` — registration validation, password hashing, lockout logic
- `OcrServiceTest` — file validation, status transitions, delegation to `OcrEngine` + `LlmService`
- `LlmServiceTest` — truncation logic, prompt construction, error handling
- `JwtUtilTest` — token generation, expiry, parsing

### Integration Tests (Spring Boot Test + Testcontainers)
- `UserRepositoryTest` — uniqueness constraints, case-insensitive username
- `OcrRecordRepositoryTest` — full-text search, pagination, user-scoped queries
- `AuthControllerIT` — login/register/logout flows with real DB
- `OcrControllerIT` — upload, search, detail, retry-summary with real DB + mocked `OcrEngine`/`LlmService`

### E2E Tests — Java (REST Assured)
- Full login → upload → search → view-detail flow against running app

### E2E Tests — Python (pytest)
- `test_auth.py` — register, login, change password, logout
- `test_ocr.py` — upload file, verify extracted text returned, verify searchable
- `test_search.py` — search with term, empty search, pagination
- `test_summary.py` — verify summary generated, retry-summary on FAILED record

### Regression Tests — Python (pytest)
- Re-run all E2E scenarios after each deployment

---

## 8. Package Structure

```
ai.medhaleak.ocrprocessor
├── OcrProcessorApplication.java
├── config/
│   ├── SecurityConfig.java          ← Spring Security + JWT filter registration
│   ├── JwtProperties.java           ← @ConfigurationProperties for JWT
│   └── AppProperties.java           ← @ConfigurationProperties for app.*
├── controller/
│   ├── AuthController.java
│   ├── HomeController.java
│   └── OcrController.java
├── service/
│   ├── UserService.java             ← interface
│   ├── UserServiceImpl.java
│   ├── OcrService.java              ← interface
│   ├── OcrServiceImpl.java
│   ├── OcrEngine.java               ← interface
│   ├── TesseractOcrEngine.java      ← implementation
│   ├── LlmService.java              ← interface
│   └── SpringAiLlmService.java      ← implementation
├── repository/
│   ├── UserRepository.java
│   └── OcrRecordRepository.java
├── model/
│   ├── User.java                    ← @Entity
│   └── OcrRecord.java               ← @Entity
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── ChangePasswordRequest.java
│   ├── OcrRecordDto.java
│   └── OcrRecordSummaryDto.java
└── exception/
    ├── OcrExtractionException.java
    ├── LlmUnavailableException.java
    ├── DuplicateUsernameException.java
    ├── UserNotFoundException.java
    └── GlobalExceptionHandler.java
```
