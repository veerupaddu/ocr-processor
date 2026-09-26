"""
conftest.py — shared fixtures for all pytest tests.

Requires the Spring Boot app running at BASE_URL.
Override with: pytest --base-url=http://localhost:8080
"""
import time
import pytest
import requests
import uuid
from pathlib import Path

from forms import login, register

PROGRESS = Path("target/test-progress.log")
STARTS = {}


def pytest_addoption(parser):
    parser.addoption("--base-url", default="http://localhost:8080",
                     help="Base URL of the running ocr-processor app")


def _case_id(nodeid):
    parts = nodeid.split("::")
    name = parts[-1]
    if len(parts) >= 3:
        return parts[-2] + "." + name
    file_name = parts[0].rsplit("/", 1)[-1].removesuffix(".py")
    return file_name + "." + name


def _write_progress(nodeid, status, duration_ms, observation, started_ms=0, ended_ms=0):
    text = " ".join(str(observation).split())[:900]
    PROGRESS.parent.mkdir(parents=True, exist_ok=True)
    with PROGRESS.open("a", encoding="utf-8") as handle:
        handle.write(f"{_case_id(nodeid)}\t{status}\t{duration_ms}\t{int(started_ms)}\t{int(ended_ms)}\t{text}\n")


def pytest_runtest_logstart(nodeid, location):
    started = int(time.time() * 1000)
    STARTS[nodeid] = started
    _write_progress(nodeid, "RUNNING", -1, "Running", started, 0)


def _observed(report):
    text = getattr(report, "capstdout", "") or ""
    facts = []
    for line in text.splitlines():
        marker = line.find("EVIDENCE:")
        if marker >= 0:
            fact = line[marker + len("EVIDENCE:"):].strip()
            if fact:
                facts.append(fact)
    return " | ".join(facts)


def pytest_runtest_logreport(report):
    if report.when == "setup" and report.passed:
        return
    if report.when == "teardown":
        return
    if report.when == "setup":
        status = "SKIPPED" if report.skipped else "FAILED"
    else:
        status = "PASSED" if report.passed else "SKIPPED" if report.skipped else "FAILED"
    evidence = _observed(report)
    if status == "PASSED" and evidence:
        observation = evidence
    elif status == "PASSED":
        observation = "Passed"
    else:
        observation = getattr(report, "longreprtext", "") or report.longrepr
    ended = int(time.time() * 1000)
    started = STARTS.get(report.nodeid, ended)
    _write_progress(report.nodeid, status, int((report.duration or 0) * 1000), observation or status, started, ended)


@pytest.fixture(scope="session")
def base_url(pytestconfig):
    return pytestconfig.getoption("--base-url")


@pytest.fixture(scope="session")
def session(base_url):
    """A requests.Session pre-configured with the base URL."""
    s = requests.Session()
    s.base_url = base_url
    return s


def _unique_user():
    uid = uuid.uuid4().hex[:8]
    return {"username": f"e2euser_{uid}", "email": f"e2euser_{uid}@example.com", "password": "TestPass123!"}


@pytest.fixture
def auth_user(base_url):
    """Register + login a fresh user; return (jwt_token, user_dict)."""
    user = _unique_user()
    register(base_url, user["username"], user["email"], user["password"])
    token = login(base_url, user["username"], user["password"])
    assert token, "Login did not return a JWT cookie"
    return token, user


@pytest.fixture
def authed_session(base_url, auth_user):
    """requests.Session with JWT cookie pre-set."""
    token, user = auth_user
    s = requests.Session()
    s.cookies.set("jwt", token)
    s.base_url = base_url
    return s
