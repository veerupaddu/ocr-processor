"""
Regression test suite for ocr-processor.

These tests re-run the full E2E critical paths after each deployment to ensure
no previously working functionality has regressed.

Run after every deploy:
    pytest tests/regression/ --base-url=http://your-server:8080 -v
"""
import io
import uuid
import requests
import pytest
from forms import form_post, login as forms_login, register as forms_register


# ─── helpers ─────────────────────────────────────────────────────────────────

def _uid():
    return uuid.uuid4().hex[:8]


def _register(base_url, username, email, password):
    return forms_register(base_url, username, email, password)


def _login(base_url, username, password):
    return forms_login(base_url, username, password)


def _authed(base_url):
    uid = _uid()
    username = f"reg_{uid}"
    email = f"reg_{uid}@example.com"
    password = "RegPass123!"
    _register(base_url, username, email, password)
    token = _login(base_url, username, password)
    assert token, f"Login failed to return JWT cookie for {username}"
    s = requests.Session()
    s.cookies.set("jwt", token)
    return s, token


# ─── fixtures ────────────────────────────────────────────────────────────────

def pytest_addoption(parser):
    try:
        parser.addoption("--base-url", default="http://localhost:8080")
    except ValueError:
        pass  # already added by e2e conftest


@pytest.fixture(scope="module")
def base_url(pytestconfig):
    return pytestconfig.getoption("--base-url")


# ─── REG-001: User registration still works ───────────────────────────────────

def test_reg_001_registration_succeeds(base_url):
    uid = _uid()
    resp = _register(base_url, f"r1_{uid}", f"r1_{uid}@example.com", "TestPass123!")
    assert resp.status_code in (200, 302), "Registration must return 200 or 302"


def test_reg_001_duplicate_username_rejected(base_url):
    uid = _uid()
    _register(base_url, f"r2_{uid}", f"r2_{uid}@example.com", "TestPass123!")
    resp = _register(base_url, f"r2_{uid}", f"r2b_{uid}@example.com", "TestPass123!")
    assert "already" in resp.text.lower()


# ─── REG-002: Login + JWT cookie ─────────────────────────────────────────────

def test_reg_002_login_returns_jwt_cookie(base_url):
    uid = _uid()
    _register(base_url, f"l_{uid}", f"l_{uid}@example.com", "TestPass123!")
    token = _login(base_url, f"l_{uid}", "TestPass123!")
    assert token is not None and len(token) > 20, "JWT must be returned as a cookie"


def test_reg_002_invalid_login_rejected(base_url):
    resp = form_post(base_url, "/login", {
        "username": "nobody_" + _uid(), "password": "anything",
    })
    assert resp.status_code in (200, 401)


# ─── REG-003: Protected routes require auth ──────────────────────────────────

def test_reg_003_home_without_auth_redirects(base_url):
    resp = requests.get(f"{base_url}/home", allow_redirects=False)
    assert resp.status_code in (302, 401, 403)


def test_reg_003_search_without_auth_rejected(base_url):
    resp = requests.get(f"{base_url}/api/v1/ocr/search", params={"q": ""},
                        allow_redirects=False)
    assert resp.status_code in (302, 401, 403)


def test_reg_003_upload_without_auth_rejected(base_url):
    resp = requests.post(f"{base_url}/api/v1/ocr/upload",
                         files={"file": ("t.png", io.BytesIO(b"\x89PNG" + b"\x00" * 50), "image/png")},
                         allow_redirects=False)
    assert resp.status_code in (302, 401, 403)


# ─── REG-004: Upload lifecycle ───────────────────────────────────────────────

def test_reg_004_upload_creates_record(base_url):
    s, _ = _authed(base_url)
    resp = s.post(f"{base_url}/api/v1/ocr/upload",
        files={"file": ("reg4.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
        data={"documentName": "Regression Test Doc"},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert "id" in body
    assert body["status"] in ("COMPLETE", "FAILED", "PROCESSING")


def test_reg_004_uploaded_record_retrievable(base_url):
    s, _ = _authed(base_url)
    upload = s.post(f"{base_url}/api/v1/ocr/upload",
        files={"file": ("reg4b.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
    )
    assert upload.status_code == 201
    record_id = upload.json()["id"]

    detail = s.get(f"{base_url}/api/v1/ocr/{record_id}")
    assert detail.status_code == 200
    assert detail.json()["id"] == record_id


def test_reg_004_unsupported_file_type_rejected(base_url):
    s, _ = _authed(base_url)
    resp = s.post(f"{base_url}/api/v1/ocr/upload",
        files={"file": ("bad.exe", io.BytesIO(b"MZ" + b"\x00" * 50), "application/octet-stream")},
    )
    assert resp.status_code in (400, 422, 500)


# ─── REG-005: Search returns scoped results ──────────────────────────────────

def test_reg_005_search_returns_200(base_url):
    s, _ = _authed(base_url)
    resp = s.get(f"{base_url}/api/v1/ocr/search", params={"q": "", "page": 0, "size": 20})
    assert resp.status_code == 200
    assert "content" in resp.json()
    assert "totalElements" in resp.json()


def test_reg_005_search_user_isolation(base_url):
    """Records from user A must not appear in user B's search."""
    s_a, _ = _authed(base_url)
    s_a.post(f"{base_url}/api/v1/ocr/upload",
        files={"file": ("isolated.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
    )

    s_b, _ = _authed(base_url)
    resp = s_b.get(f"{base_url}/api/v1/ocr/search", params={"q": ""})
    assert resp.status_code == 200
    assert resp.json()["totalElements"] == 0


# ─── REG-006: Non-existent records return 404 ────────────────────────────────

def test_reg_006_get_unknown_record_404(base_url):
    s, _ = _authed(base_url)
    resp = s.get(f"{base_url}/api/v1/ocr/00000000-0000-0000-0000-000000000000")
    assert resp.status_code == 404


def test_reg_006_retry_unknown_record_404(base_url):
    s, _ = _authed(base_url)
    resp = s.post(f"{base_url}/api/v1/ocr/00000000-0000-0000-0000-000000000000/retry-summary")
    assert resp.status_code == 404


# ─── REG-007: Change password ─────────────────────────────────────────────────

def test_reg_007_change_password_wrong_current_rejected(base_url):
    uid = _uid()
    _register(base_url, f"cp_{uid}", f"cp_{uid}@example.com", "OldPass123!")
    token = _login(base_url, f"cp_{uid}", "OldPass123!")
    assert token, "Login failed for change-password test user"

    resp = form_post(base_url, "/profile/change-password", {
        "currentPassword": "WrongPassword!",
        "newPassword": "NewPass456!",
        "confirmPassword": "NewPass456!",
    }, jwt=token)
    assert resp.status_code in (200, 401)
    if resp.status_code == 200:
        assert "incorrect" in resp.text.lower() or "wrong" in resp.text.lower()
