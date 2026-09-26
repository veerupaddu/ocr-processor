# Design Document: Test Console

## Overview

The home page provides a dedicated **Tests** tab beside Search and Create. Every test case is browsed and executed through the browser interface.

The tab organizes testing into four sub-tabs: **Unit** (32 cases), **Regression** (29 cases), **End to end** (40 cases), and **Coverage** (functional traceability). Each sub-tab presents its respective test cases alongside an execution control toolbar and results table. A test case row is populated before any execution, displaying the case identifier, human-readable name, and expected outcome contract. Execution status, timing, and triple-fold evidence populate upon execution.

Authenticated users execute entire suites using the suite run action, or select specific test cases via checkboxes to execute targeted runs. Execution runs in a dedicated background process and streams progress into the UI by parsing the resulting JUnit XML reports. Unselected rows maintain their current results. The test runner operates in an isolated worker process separate from the server runtime.

## Coverage & Suite Breakdown

The platform encompasses 101 automated test cases across three execution suites:

| Suite | Automated Cases | Scope & Assertions | Execution Engine |
|---|---|---|---|
| **Unit** | 32 cases | Service and engine behaviour with isolated dependencies, token verification, lockout rules, truncation boundaries, and image OCR readability | Maven Surefire running JUnit 5 test classes (`target/surefire-reports`) |
| **Integration** | 29 cases | Full HTTP, Spring Security, and database transaction lifecycles against PostgreSQL managed via Testcontainers | Maven Surefire running `*IT` integration test classes |
| **End to end** | 40 cases | Complete browser user journeys via Selenium WebDriver against the live server, combined with automated API journeys | Python pytest running Selenium with Chrome Headless and requests API tests |

### What Each Layer Proves

- **Unit (32 cases):** Verifies JWT claim generation and tamper rejection, registration validation, account lockout timers, upload orchestration (success, OCR failure, summary failure, empty text, unsupported format, retry), prompt template bounds (10,000 characters), OCR confidence filters, and real Tesseract text extraction of rendered test fixtures. Includes 3 deliberate failure samples (`FailureSamplesTest`) demonstrating root-cause diagnostic capabilities.
- **Integration (29 cases):** Validates authentication, upload, search, and profile operations through HTTP requests against an active Testcontainers PostgreSQL instance. Proves cookie session management, multi-user owner isolation, validation error payloads, and full-text search pagination.
- **End to end (40 cases):** Validates full user workflows in the browser: account registration, credential submission, three-tab navigation, drag-and-drop document upload, OCR preview cards, and logout. Combined with pytest scenarios verifying search query filtering, pagination offsets, and summary retry triggers.

### Requirements Traceability Dashboard

The **Coverage** sub-tab provides direct traceability to `REQ-001` through `REQ-007` from the requirements phase.
- **Overall Reach:** 38 of 52 acceptance criteria validated (73.1% functional coverage).
- Visual progress bars depict the percentage of criteria covered per functional story.
- Detailed criteria breakdowns document verified capabilities and provide transparent explanations for criteria remaining open (such as >20 MB uploads, live model billing limits, and multi-minute lockout wait periods).

## Architecture

```
Home page (Tests tab)
    │  GET /api/v1/tests
    │  POST /api/v1/tests/{suite}/run   body optional: { "cases": ["id", ...] }
    ▼
TestConsoleController  (authenticated, same JWT cookie as the rest of the app)
    ▼
TestConsoleService
    ├── catalog: classpath test-catalog.json  (name + expected result, stable)
    └── one background run
            ├── unit / integration → mvn test → parse surefire XML
            └── e2e → pytest (Selenium + existing API e2e) → parse JUnit XML
```

Only one suite runs at a time. Another click is queued and starts when the current run finishes. Checkboxes stay usable, so the next set can be chosen while a run is in progress. Results live in memory for the life of the server process. Restarting the app clears times and observations; the expected results stay, because they come from the catalog.

The end-to-end run calls back into this same server at `http://localhost:{port}`. Unit and integration runs execute in independent Spring contexts with an isolated Testcontainers database.

## Components and Interfaces

### Catalog

`src/main/resources/test-catalog.json` is the list the page renders before anything has run. Each case has:

- `id` — `SimpleClassName.methodName`, which is how a JUnit report row is matched
- `name` — short label shown in the table
- `expected` — the result the case is written to prove

The execution runner strictly matches cases defined in the catalog. Cases in the report correspond directly to catalog definitions, while unexecuted cases remain in the initial state.

### Run

`POST /api/v1/tests/{unit|integration|e2e}/run` returns 202 and the current snapshot. The work continues on a single background thread. A JSON body `{ "cases": ["JwtUtilTest.generateAndParse_roundtrip"] }` runs only those catalog ids. An empty list is rejected. A missing body runs the whole section. Surefire receives `Class#method+method`. Pytest receives the node ids for those cases.

`GET /api/v1/tests` returns every suite:

- suite status: `IDLE`, `RUNNING`, `PASSED`, `FAILED`
- suite observation (Maven or pytest tail when execution terminates prematurely)
- cases: `id`, `name`, `expected`, `status` (`NOT_RUN`, `PASSED`, `FAILED`, `ERROR`, `SKIPPED`), `durationMs`, `observation`

A passed case records three fields from that run: the input data, the execution status, and the generated output. A failure records the assertion message from the XML, truncated so the table stays readable.

### Page

`/home` has three top-level tabs: Search, Create, and Tests. Tests is the screen for this feature.

```
┌ Tests ─────────────────────────────────────────────────────────┐
│ [Unit]  [Regression]  [End to end]                             │
│                         [Run unit tests] [Run selected (2)]    │
│ ☐ | Case | Expected result | Result | Time | Observations     │
│ …cases for the open sub-tab…                                   │
└────────────────────────────────────────────────────────────────┘
```

One sub-tab is open at a time. The other two keep their results and can be opened while a run is in progress. Failed and error cases are listed first. Every other case stays in catalog order.

Each case is two rows, so the table fits the page width. The first row is the case, the expected result, the result, and the times. The second row is what was executed, what was validated, what was observed, and the screenshot.

Columns on the first row:

| Column | Before a run | While that case runs | After the case finishes |
|---|---|---|---|
| Select | Checkbox, empty | Unchanged | Unchanged |
| # | 1, 2, 3… within the section | Unchanged | Unchanged |
| Case | Name and id from the catalog | Unchanged | Unchanged |
| Expected result | The result the case is written to prove | Unchanged | Unchanged |
| Result | Pending | Running | Passed, Failed, Error, or Skipped |
| Started | — | The clock time the case began | Unchanged |
| Ended | — | — | The clock time the case finished |
| Duration | — | — | How long the case took |
| Executed | — | — | What the case did |
| Validated | — | — | What the case checked |
| Observed | — | — | The values that came back, or the failure message |
| Screenshot | — | — | The page after a browser case, or a card of the same fields for every other case |
| Result, when failed | — | — | A **Details** button opens where it failed, why, and how to fix it |

Pressing **Run** on a section sends `POST /api/v1/tests/{unit\|integration\|e2e}/run`. Pressing **Run selected** sends the same request with the ticked case ids. The program starts that suite and, as each selected case starts and finishes, writes the result into the row that is already on screen. The page polls about once a second, so the table updates without waiting for the whole suite. The header checkbox selects every case in that section. **Run selected** stays disabled until at least one case in that section is ticked.

### Selenium

`tests/e2e/selenium/test_ui.py` uses Selenium 4 and Chrome in headless mode. Selenium Manager resolves the driver. When the case finishes, Chrome saves `target/test-screenshots/{caseId}.png`. The Tests tab shows that image under the observation. `GET /api/v1/tests/screenshots/{caseId}` serves the PNG to a signed-in user. Cases:

| Case | Expected result |
|---|---|
| `test_ui.test_home_without_login_redirects` | `/home` without a cookie lands on the login page |
| `test_ui.test_register_reaches_login` | A new account ends on the login page |
| `test_ui.test_login_invalid_password_shows_error` | A wrong password stays on login and shows an error |
| `test_ui.test_login_opens_home_tabs` | A valid login shows Search, Create, and Tests |
| `test_ui.test_upload_png_shows_extraction` | Uploading a HELLO PNG shows a completed extraction |
| `test_ui.test_search_panel_returns_results` | Search renders a result list or the empty state |
| `test_ui.test_logout_returns_to_login` | Logout returns to the login page |

## Data Models

Catalog case (file): `id`, `name`, `expected`.

Case result (memory): those three fields plus `status`, `durationMs`, `observation`. A failure also has `where`, `why`, and `fix`.

Suite snapshot: `id`, `title`, `status`, `observation`, `running`, `passed`, `failed`, `cases`.

Execution state is maintained in-memory for the active runtime session.

## Error Handling

| Situation | What the user sees |
|---|---|
| Missing Maven or pytest binary | Suite status FAILED. Observation names the missing tool. Cases remain in pending state |
| Tests fail | Suite FAILED. Each failed row carries the assertion message, its time, and a Details button. Details names the file and line, the expected and actual values, and the change that makes the case pass |
| Docker is down during integration | The Testcontainers error is the suite observation |
| Chrome is missing during end to end | The Selenium error is the observation on the browser cases |
| A run is already active | HTTP 409. The button stays disabled until the active run finishes |
| Request body specifies an unrecognized case identifier | HTTP 400. The observation identifies the unrecognized case |
| The process exceeds its limit (unit 8 min, integration 20 min, end to end 15 min) | The process is stopped. Observation says the suite timed out |

## Testing Strategy

- A unit test parses a fixture JUnit XML and checks status, time, and the failure observation.
- A unit test loads `test-catalog.json` and checks that each suite has the cases this document lists.
- The page itself is checked in the browser: open Tests, switch all three suites, and confirm every row has an expected result before a run.
