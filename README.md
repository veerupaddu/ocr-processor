# IBM Bob — In-App Automation Testing Platform

[![CI](https://github.com/veerupaddu/ocr-processor/actions/workflows/ci.yml/badge.svg)](https://github.com/veerupaddu/ocr-processor/actions/workflows/ci.yml)
[![Repository](https://img.shields.io/badge/GitHub-ocr--processor-blue)](https://github.com/veerupaddu/ocr-processor.git)

An enterprise document intelligence application (`ocr-processor`) featuring an embedded, in-app automation test console that bridges the developer–QA gap with interactive test execution, real-time evidence capture, root-cause failure diagnostics, and requirements-phase traceability.

Architected and developed across four structured engineering phases co-piloted with **IBM Bob**:
1. **Phase 1: Requirements Engineering** — 7 functional user stories (`REQ-001` through `REQ-007`) with 52 acceptance criteria in EARS format.
2. **Phase 2: System Architecture & ADRs** — 5 Architecture Decision Records covering Thymeleaf, Tesseract OCR, Spring AI (DeepSeek & OpenAI), JWT cookie authentication, and PostgreSQL full-text search.
3. **Phase 3: Technical Design** — Wireframes, responsive UI grids, database entity-relationship models, and sequence diagrams.
4. **Phase 4: Implementation & Test Engineering** — Full-stack Spring Boot service with 101 automated test cases across three test tiers.

---

## Executive Presentation Deck

The project includes an executive presentation deck summarizing the business problem, architectural design, testing innovations, and measurable SDLC impacts:

- **PowerPoint Deck:** [`ocr-processor-presentation.pptx`](ocr-processor-presentation.pptx) (10 widescreen 16:9 slides with embedded UI screenshots)
- **PDF Export:** [`ocr-processor-presentation_v1.pdf`](ocr-processor-presentation_v1.pdf)
- **Deck Generator:** [`build_deck.py`](build_deck.py) (reproducible Python automation using `python-pptx`)

### Slide Summary
- **Slide 1:** Title & Project Context (Co-piloted with IBM Bob)
- **Slide 2:** The Core Testing Problem (Status Quo Friction & Pipeline Gaps)
- **Slide 3:** The Solution (In-App Automation Testing Paradigm)
- **Slide 4:** The Product Under Test (Document Intelligence Pipeline)
- **Slide 5:** The Test Console (101 Automated Cases Across 3 Tiers)
- **Slide 6:** Actionable Evidence & Root-Cause Failure Diagnostics
- **Slide 7:** Requirements-Phase Traceability (73.1% Functional Coverage)
- **Slide 8:** Co-Piloted with IBM Bob (Lifecycle Stages & Artifacts)
- **Slide 9:** Measurable SDLC Impact (Latency, Confidence, Handoff Quality)
- **Slide 10:** Summary & Next Horizons

---

## Core System Architecture (`ocr-processor`)

```
                          ┌──────────────────────────────────────┐
                          │     Browser / Client Interaction     │
                          │   /register, /login, /home, /tests   │
                          └──────────────────┬───────────────────┘
                                             │ HTTP / JWT Cookie
                                             ▼
                          ┌──────────────────────────────────────┐
                          │   Spring Boot 3.3 Application Core   │
                          ├──────────────────────────────────────┤
                          │  • SecurityConfig & JwtAuthFilter    │
                          │  • HomeController & Thymeleaf UI     │
                          │  • OcrController & Document APIs     │
                          │  • TestConsoleController & Service   │
                          └──────┬───────────┬───────────┬───────┘
                                 │           │           │
                 ┌───────────────┘           │           └────────────────┐
                 ▼                           ▼                            ▼
┌─────────────────────────────────┐ ┌──────────────────┐ ┌────────────────────────────────┐
│       PostgreSQL 16 Engine      │ │  Tesseract 5 OCR │ │      Spring AI Framework       │
├─────────────────────────────────┤ ├──────────────────┤ ├────────────────────────────────┤
│ • Relational users & documents  │ │ • Local CLI exec │ │ • OpenAI / Local LLM client    │
│ • TSVECTOR full-text search     │ │ • ImageMagick    │ │ • Automatic summarisation      │
│ • GIN indexing & user isolation │ │ • Preprocessing  │ │ • One-click retry resilience   │
└─────────────────────────────────┘ └──────────────────┘ └────────────────────────────────┘
```

---

## In-App Test Console (`/home#tests`)

The Test Console enables developers and QA engineers to trigger and inspect test runs directly within the running application.

### 1. Test Tiers (101 Automated Cases)
- **Unit Tests (32 cases):** Lightning-fast service-level checks verifying JWT token claims and tamper rejection, account lockout thresholds, prompt formatting, OCR confidence filtering, and input bounds. Includes 3 intentional failure samples (`FailureSamplesTest`) for demonstrating failure diagnostics.
- **Regression Tests (29 cases):** Integration tests executing against a real Testcontainers PostgreSQL database, validating transactional boundaries, multi-user data isolation, password modification flows, and full-text search querying.
- **End-to-End Tests (40 cases):** Realistic user journeys driven by Selenium and Python pytest verifying registration, login, multi-tab switching, drag-and-drop document upload, and OCR summary cards.

### 2. Triple-Fold Evidence Model
Each test run automatically captures and renders:
- **Executed:** The exact parameters, authenticated user identity, payload contents, and headers passed to the system.
- **Validated:** The assertions evaluated, expected response contracts, and database schema guarantees.
- **Observed:** The actual response payload, database state mutations, elapsed execution time, and rendered UI screenshots.

### 3. Requirements-Phase Traceability Dashboard
Connects automated test execution back to the original functional requirements:
- **Coverage Reach:** 38 of 52 acceptance criteria validated (73.1% functional coverage).
- **Traced Requirements:** `REQ-001` (Registration), `REQ-002` (Login), `REQ-003` (Change Password), `REQ-004` (Home UI), `REQ-005` (Search), `REQ-006` (Upload & OCR), and `REQ-007` (Summary).
- **Transparent Accounting:** Documents why remaining edge cases (e.g., uploads exceeding 20 MB, third-party model billing limits, network disconnects) remain open for specialized environment testing.

---

## Repository Structure

```
.
├── .gitignore                               # Workspace-level ignore rules
├── README.md                                # This file
├── build_deck.py                            # Presentation automation generator
├── ocr-processor-presentation.pptx          # Executive presentation deck (PPTX)
├── ocr-processor-presentation_v1.pdf        # Executive presentation deck (PDF)
│
└── ocr-processor/                           # Full-stack Spring Boot application
    ├── .gitignore                           # Java/Maven/Python ignore rules
    ├── .env.example                         # Environment template
    ├── pom.xml                              # Maven build descriptor
    ├── pytest.ini                           # Pytest configuration
    ├── Dockerfile                           # Container definition
    ├── docker-compose.yml                   # Local PostgreSQL orchestration
    ├── docs/                                # Engineering lifecycle specifications
    │   ├── requirements/REQUIREMENTS.md     # Phase 1: 52 criteria across 7 stories
    │   ├── architecture/                    # Phase 2: System spec & 5 ADRs
    │   │   ├── ARCHITECTURE.md
    │   │   ├── TECH_SPEC.md
    │   │   └── adr/
    │   └── design/                          # Phase 3: Wireframes & design spec
    │       ├── DESIGN.md
    │       └── TEST_CONSOLE.md
    ├── src/
    │   ├── main/
    │   │   ├── java/ai/medhaleak/ocrprocessor/   # Application source code
    │   │   └── resources/                        # Templates, static assets, migrations
    │   └── test/
    │       ├── java/ai/medhaleak/ocrprocessor/   # Java unit & integration suites
    │       └── resources/                        # Test configurations & fixtures
    └── tests/                                    # Python pytest & Selenium E2E suite
        ├── e2e/
        ├── regression/
        └── requirements.txt
```

---

## Getting Started

### Prerequisites
- **Java 21+** (`java -version`)
- **Maven 3.9+** (`mvn -v`)
- **PostgreSQL 16** (or Docker for `docker-compose`)
- **Tesseract OCR CLI** (`brew install tesseract` on macOS or `sudo apt-get install tesseract-ocr` on Linux)
- **Python 3.12+** (for Selenium and pytest end-to-end suites)

### Setup & Launch

1. **Configure Environment:**
   ```bash
   cd ocr-processor
   cp .env.example .env
   # Set SPRING_DATASOURCE_URL, JWT_SECRET, and LLM_API_KEY (DeepSeek or OpenAI)
   ```

2. **Start Database:**
   ```bash
   docker-compose up -d postgres
   ```

3. **Build and Run:**
   ```bash
   mvn clean spring-boot:run
   ```

4. **Access Application:**
   Open [http://localhost:8080](http://localhost:8080) in your browser:
   - Register a new account or log in.
   - Navigate to the **Tests** tab to run automated suites and view real-time evidence.

### Running Test Suites via CLI

- **Java Unit and Integration Tests:**
  ```bash
  mvn test
  ```

- **Python End-to-End and Selenium Suites:**
  ```bash
  cd tests
  python3 -m venv .venv
  source .venv/bin/activate
  pip install -r requirements.txt
  pytest e2e/ --base-url=http://localhost:8080 -v
  ```
