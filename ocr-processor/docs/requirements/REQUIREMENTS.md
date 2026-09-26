# Requirements Document: ocr-processor

> **Status:** 🟡 Draft  
> **Phase:** 1 of 6  
> **Last Updated:** 2025-07-14

## Overview

`ocr-processor` is a Spring Boot REST API + web application that allows authenticated users to upload documents (images/PDFs), extract text via OCR, store results in PostgreSQL, and use an LLM to summarise the extracted content. A home page with **Search** and **Create** tabs serves as the primary interface.

---

## User Roles

| Role | Description |
|---|---|
| **Guest** | Unauthenticated visitor — can only access login and registration screens |
| **Registered User** | Authenticated user with full access to search, upload, OCR, and LLM summary features |
| **Admin** | *(Out of scope for this phase)* |

---

## Requirements

---

### REQ-001: User Registration

**User Story:**  
As a guest, I want to register an account with a username and password, so that I can access the OCR features.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-001-01 | WHEN a guest navigates to `/register` THEN the system SHALL display a registration form with fields: username, email, password, confirm password |
| REQ-001-02 | WHEN a guest submits valid registration details THEN the system SHALL create a new user account and redirect to the login page |
| REQ-001-03 | WHEN a guest submits a username that already exists THEN the system SHALL display the error: "Username already taken" |
| REQ-001-04 | WHEN a guest submits an email that already exists THEN the system SHALL display the error: "Email already registered" |
| REQ-001-05 | WHEN a guest submits a password shorter than 8 characters THEN the system SHALL display: "Password must be at least 8 characters" |
| REQ-001-06 | WHEN a guest submits mismatched password and confirm password THEN the system SHALL display: "Passwords do not match" |
| REQ-001-07 | WHEN a guest submits an invalid email format THEN the system SHALL display: "Invalid email address" |
| REQ-001-08 | WHEN registration is successful THEN the system SHALL store the password as a bcrypt hash (never plaintext) |
| REQ-001-09 | WHEN any required field is left blank THEN the system SHALL display a field-level validation error |

**Edge Cases:**
- Username and email fields SHALL be trimmed of leading/trailing whitespace before validation
- Username SHALL be case-insensitive for uniqueness checks (e.g. `Alice` and `alice` treated as the same)
- Maximum username length: 50 characters
- Maximum email length: 254 characters (RFC 5321)
- Maximum password length: 128 characters

---

### REQ-002: User Login

**User Story:**  
As a registered user, I want to log in with my username and password, so that I can access the application.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-002-01 | WHEN a guest navigates to `/login` THEN the system SHALL display a login form with username and password fields |
| REQ-002-02 | WHEN a guest submits valid credentials THEN the system SHALL authenticate the user and redirect to the home page (`/home`) |
| REQ-002-03 | WHEN a guest submits invalid credentials THEN the system SHALL display: "Invalid username or password" (no hint as to which is wrong) |
| REQ-002-04 | WHEN a guest leaves username or password blank THEN the system SHALL display a field-level validation error |
| REQ-002-05 | WHEN authentication is successful THEN the system SHALL issue a JWT token with a configurable expiry (default 8 hours) |
| REQ-002-06 | WHEN an unauthenticated user attempts to access a protected route THEN the system SHALL redirect to `/login` |
| REQ-002-07 | WHEN a user fails login 5 consecutive times THEN the system SHALL lock the account for 15 minutes and display: "Account temporarily locked. Try again later." |
| REQ-002-08 | WHEN a user logs out THEN the system SHALL invalidate the JWT and redirect to `/login` |

**Edge Cases:**
- Login SHALL be rate-limited to prevent brute-force attacks
- JWT SHALL be stored in an HTTP-only cookie (not localStorage)

---

### REQ-003: Change Password

**User Story:**  
As a registered user, I want to change my password, so that I can maintain account security.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-003-01 | WHEN an authenticated user navigates to `/profile/change-password` THEN the system SHALL display a form with: current password, new password, confirm new password |
| REQ-003-02 | WHEN a user submits valid current and new passwords THEN the system SHALL update the password hash and display: "Password changed successfully" |
| REQ-003-03 | WHEN a user submits an incorrect current password THEN the system SHALL display: "Current password is incorrect" |
| REQ-003-04 | WHEN a user submits a new password shorter than 8 characters THEN the system SHALL display the validation error |
| REQ-003-05 | WHEN new password and confirm new password do not match THEN the system SHALL display: "Passwords do not match" |
| REQ-003-06 | WHEN the new password is identical to the current password THEN the system SHALL display: "New password must differ from current password" |
| REQ-003-07 | IF a user is not authenticated THEN the system SHALL redirect to `/login` |

---

### REQ-004: Home Page with Search and Create Tabs

**User Story:**  
As an authenticated user, I want a home page with Search and Create tabs, so that I can quickly navigate between finding existing records and uploading new ones.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-004-01 | WHEN an authenticated user navigates to `/home` THEN the system SHALL display a home page with two tabs: **Search** (default active) and **Create** |
| REQ-004-02 | WHEN the user clicks the **Search** tab THEN the system SHALL display the search interface (REQ-005) without a full page reload |
| REQ-004-03 | WHEN the user clicks the **Create** tab THEN the system SHALL display the OCR upload interface (REQ-006) without a full page reload |
| REQ-004-04 | WHEN the home page loads THEN the system SHALL show a navigation bar with the logged-in username and a Logout button |
| REQ-004-05 | WHEN the user clicks Logout THEN the system SHALL invalidate the session and redirect to `/login` |

---

### REQ-005: Search OCR Records

**User Story:**  
As an authenticated user, I want to search previously extracted OCR content, so that I can quickly retrieve documents I have processed.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-005-01 | WHEN the Search tab is active THEN the system SHALL display a search input field and a **Search** button |
| REQ-005-02 | WHEN the user submits a search term THEN the system SHALL query the OCR records and display matching results within 2 seconds |
| REQ-005-03 | WHEN results are returned THEN the system SHALL display each result with: document name, upload date, status, and a truncated preview of the extracted text (max 200 characters) |
| REQ-005-04 | WHEN no results match the search THEN the system SHALL display: "No records found for your search." |
| REQ-005-05 | WHEN results exceed 20 records THEN the system SHALL paginate with 20 results per page and display page navigation |
| REQ-005-06 | WHEN the user clicks a result THEN the system SHALL open a detail view showing the full extracted text, LLM summary, file metadata, and upload date |
| REQ-005-07 | WHEN the user submits an empty search THEN the system SHALL display all records belonging to the authenticated user, paginated |
| REQ-005-08 | WHEN the user types in the search box THEN the system SHALL search across: document name, extracted text content, and LLM summary |

**Edge Cases:**
- Search input SHALL be sanitised to prevent SQL injection
- Search SHALL be scoped to records owned by the authenticated user only
- Special characters in the search term SHALL be escaped before querying

---

### REQ-006: Upload Document and Extract OCR

**User Story:**  
As an authenticated user, I want to upload a document and have text extracted via OCR, so that the content is searchable and stored.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-006-01 | WHEN the Create tab is active THEN the system SHALL display a file upload area (drag-and-drop or click-to-browse), a **Document Name** input, and an **Upload & Extract** button |
| REQ-006-02 | WHEN a user uploads a supported file THEN the system SHALL accept it, display a progress indicator, run OCR extraction, and store the result in PostgreSQL |
| REQ-006-03 | WHEN OCR extraction completes THEN the system SHALL display the extracted text in a preview panel on the same screen |
| REQ-006-04 | WHEN a user uploads a file larger than 20 MB THEN the system SHALL reject it and display: "File too large. Maximum size is 20 MB." |
| REQ-006-05 | WHEN a user uploads an unsupported file type THEN the system SHALL display: "Unsupported file type. Allowed: PDF, PNG, JPG, JPEG, TIFF." |
| REQ-006-06 | WHEN OCR extraction fails THEN the system SHALL set the record status to `FAILED`, display: "OCR extraction failed. Please try again.", and log the error |
| REQ-006-07 | WHEN a file is uploaded without a document name THEN the system SHALL default to the original filename |
| REQ-006-08 | WHEN a record is saved THEN the system SHALL store: document name, original filename, file type, file size, extracted text, status (`PENDING` → `PROCESSING` → `COMPLETE` / `FAILED`), upload timestamp, and owner user ID |

**Supported File Types:** PDF, PNG, JPG, JPEG, TIFF  
**Maximum File Size:** 20 MB

**Edge Cases:**
- Empty or blank documents (no text extractable) SHALL be saved with status `COMPLETE` and `extractedText` = empty string; the user SHALL be notified: "No text could be extracted from this document."
- Concurrent uploads by the same user SHALL be supported (no locking required in v1)

---

### REQ-007: LLM Summarisation

**User Story:**  
As an authenticated user, I want the system to automatically generate a summary of extracted OCR text using an LLM, so that I can understand long documents quickly.

**Acceptance Criteria:**

| ID | Requirement |
|---|---|
| REQ-007-01 | WHEN OCR extraction completes with non-empty text THEN the system SHALL automatically invoke the configured LLM to generate a summary |
| REQ-007-02 | WHEN summarisation completes THEN the system SHALL store the summary in the OCR record and display it in the Create tab preview panel |
| REQ-007-03 | WHEN the LLM service is unavailable THEN the system SHALL store the record without a summary, set `summaryStatus` = `FAILED`, and display: "Summary unavailable. You can retry later." |
| REQ-007-04 | WHEN extracted text exceeds 10,000 characters THEN the system SHALL truncate to 10,000 characters before sending to the LLM |
| REQ-007-05 | WHEN extracted text is empty THEN the system SHALL skip LLM summarisation and leave the summary field blank |
| REQ-007-06 | WHEN a user views a record with `summaryStatus` = `FAILED` THEN the system SHALL display a **Retry Summary** button |
| REQ-007-07 | WHEN the user clicks **Retry Summary** THEN the system SHALL re-invoke the LLM and update the record |

**LLM Integration:**
- Provider: configurable via `application.yml` (default: OpenAI GPT-4o-mini)
- API key: supplied via environment variable `LLM_API_KEY` (never hardcoded)
- Prompt template: configurable

---

## Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Performance** | Search results SHALL be returned within 2 seconds for datasets up to 10,000 records |
| **Performance** | OCR extraction SHALL complete within 30 seconds for files up to 20 MB |
| **Security** | All endpoints except `/login` and `/register` SHALL require a valid JWT |
| **Security** | Passwords SHALL be stored using bcrypt with a minimum cost factor of 12 |
| **Security** | API keys and secrets SHALL be supplied via environment variables, never committed to source |
| **Security** | All user data access SHALL be scoped to the authenticated user (no cross-user data leakage) |
| **Availability** | The application SHALL start and be ready to serve requests within 30 seconds |
| **Scalability** | The application SHALL support at least 50 concurrent users in v1 |
| **Auditability** | All OCR upload events SHALL be logged with user ID, timestamp, and file metadata |

---

## Data Model (Summary)

### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `username` | VARCHAR(50) | Unique, case-insensitive |
| `email` | VARCHAR(254) | Unique |
| `password_hash` | VARCHAR(255) | bcrypt |
| `failed_login_attempts` | INT | For lockout |
| `locked_until` | TIMESTAMP | Nullable |
| `created_at` | TIMESTAMP | |

### `ocr_records`
| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | FK → users |
| `document_name` | VARCHAR(255) | |
| `original_filename` | VARCHAR(255) | |
| `file_type` | VARCHAR(20) | PDF / PNG / JPG etc |
| `file_size_bytes` | BIGINT | |
| `extracted_text` | TEXT | Nullable |
| `summary` | TEXT | Nullable |
| `status` | VARCHAR(20) | PENDING / PROCESSING / COMPLETE / FAILED |
| `summary_status` | VARCHAR(20) | PENDING / COMPLETE / FAILED / SKIPPED |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

---

## Out of Scope (v1)

- Admin panel / user management by admin
- Multi-factor authentication (MFA)
- File storage in cloud (S3 etc.) — files processed in-memory in v1
- OCR language selection
- Batch upload (multiple files at once)
- Email notifications
- Password reset via email

---

## Open Questions

| # | Question | Owner |
|---|---|---|
| OQ-1 | Which OCR library to use — Tesseract (open-source) or a cloud API (Google Vision, AWS Textract)? | Architecture phase |
| OQ-2 | Should the frontend be server-rendered (Thymeleaf) or a separate SPA (React)? | Architecture phase |
| OQ-3 | Which LLM provider is preferred — OpenAI, Anthropic, or IBM watsonx? | Stakeholder |
| OQ-4 | Are there any compliance/data-residency requirements for storing extracted text? | Stakeholder |
