# Design Document: Test Console

## Overview

The home page has a **Tests** tab beside Search and Create. That tab is where every case is read and where every run is started. Nothing in this screen is started from a terminal.

The tab has four sub-tabs, in this order: **Unit**, **Regression**, **End to end**, **Coverage**. Each sub-tab shows only that suite’s cases, with its own **Run** button and table. A row is visible before any run. It already contains the case name and the expected result. Result, time, and observation fill in after Run.

A signed-in user starts one suite at a time by pressing that section’s Run button. Tick the checkbox on one or more rows and press **Run selected** to run only those cases. The app runs them in a separate process and fills the table from the JUnit XML the runner writes. Rows that were not selected keep the result they already have. The running server is not the test JVM.

## Coverage

| Suite | What it proves | Where the cases live | How Run executes them |
|---|---|---|---|
| Unit | Service and engine behaviour with collaborators mocked, plus one real Tesseract read of a rendered image | `src/test/java/**/unit/**` and `TesseractOcrEngineReadabilityTest` | `mvn test` on those classes. Surefire reports under `target/surefire-reports` |
| Integration | HTTP, security, and PostgreSQL together. Postgres comes from Testcontainers, so Docker must be running | `src/test/java/**/integration/**` | `mvn test` on the `*IT` classes |
| End to end | A real browser session against this server, and the existing API journeys in `tests/e2e` | `tests/e2e/selenium` and `tests/e2e/test_*.py` (placeholders excluded) | pytest. Selenium drives Chrome. The API files keep using `requests` |

`tests/regression` stays out of this tab. It repeats the same journeys for CI and is not a fourth layer.

### What each layer is responsible for

- **Unit** covers JWT issue and reject, registration and lockout rules, upload orchestration (success, OCR failure, summary failure, empty text, unsupported type, retry), LLM truncation and error wrapping, Tesseract confidence filtering, and a real OCR read of the word HELLO.
- **Integration** covers the same auth and OCR flows through HTTP with a real database: cookie login, validation errors, upload and search, owner isolation, and repository paging. OCR and the model are stubbed in that suite so the HTTP and database behaviour can be asserted exactly.
- **End to end** covers what a person can do in the browser: register, log in, see the three home tabs, upload a PNG, search, and log out. The existing pytest modules (`test_auth`, `test_ocr`, `test_search`, `test_summary`) stay in the same table so their expected results and times are visible too.

No coverage-percentage gate is part of this console. The table is the record of which cases ran and what they observed. The **Coverage** sub-tab follows REQ-001 through REQ-007 from the requirements phase. Each bar is the share of that function’s acceptance criteria that have a case. The line under the bar names what is covered and why the remaining criteria stay open. Items the requirements mark as out of scope for v1 sit on their own row.

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

The end-to-end run calls back into this same server at `http://localhost:{port}`. Unit and integration runs do not. They boot their own Spring contexts. Integration uses a Testcontainers database, not the database this server is using.

## Components and Interfaces

### Catalog

`src/main/resources/test-catalog.json` is the list the page renders before anything has run. Each case has:

- `id` — `SimpleClassName.methodName`, which is how a JUnit report row is matched
- `name` — short label shown in the table
- `expected` — the result the case is written to prove

The Run button does not invent cases. If a report row has no catalog entry, it is ignored. If a catalog case never appears in the report, it stays **Not run** and the observation says so.

### Run

`POST /api/v1/tests/{unit|integration|e2e}/run` returns 202 and the current snapshot. The work continues on a single background thread. A JSON body `{ "cases": ["JwtUtilTest.generateAndParse_roundtrip"] }` runs only those catalog ids. An empty list is rejected. A missing body runs the whole section. Surefire receives `Class#method+method`. Pytest receives the node ids for those cases.

`GET /api/v1/tests` returns every suite:

- suite status: `IDLE`, `RUNNING`, `PASSED`, `FAILED`
- suite observation (Maven or pytest tail when the process could not produce reports)
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
| Result | Not run | Running | Passed, Failed, Error, or Skipped |
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

No database table. This screen is a view over the last run, not a history.

## Error Handling

| Situation | What the user sees |
|---|---|
| Maven or pytest is not on the machine | Suite status FAILED. Observation names the missing tool. Cases stay Not run |
| Tests fail | Suite FAILED. Each failed row carries the assertion message, its time, and a Details button. Details names the file and line, the expected and actual values, and the change that makes the case pass |
| Docker is down during integration | The Testcontainers error is the suite observation |
| Chrome is missing during end to end | The Selenium error is the observation on the browser cases |
| A run is already active | HTTP 409. The button stays disabled until the active run finishes |
| The body names a case that is not in that section | HTTP 400. The observation says the case is unknown |
| The process exceeds its limit (unit 8 min, integration 20 min, end to end 15 min) | The process is stopped. Observation says the suite timed out |

## Testing Strategy

- A unit test parses a fixture JUnit XML and checks status, time, and the failure observation.
- A unit test loads `test-catalog.json` and checks that each suite has the cases this document lists.
- The page itself is checked in the browser: open Tests, switch all three suites, and confirm every row has an expected result before a run.
