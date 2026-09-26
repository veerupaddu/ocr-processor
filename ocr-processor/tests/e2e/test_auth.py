"""
test_auth.py — E2E tests for registration, login, change password, logout.
"""
import uuid
import requests
import pytest
from forms import register, login
from evidence import record, visible
from forms import form_post


class TestRegistration:

    def test_register_success_redirects(self, base_url):
        uid = uuid.uuid4().hex[:8]
        resp = register(base_url, f"reg_{uid}", f"reg_{uid}@example.com", "TestPass123!")
        record(f"username=reg_{uid} email=reg_{uid}@example.com password=TestPass123!",
               f"HTTP {resp.status_code}", f"Location={resp.headers.get('Location')}")
        assert resp.status_code == 302
        assert "/login" in resp.headers.get("Location", "")

    def test_register_duplicate_username_shows_error(self, base_url):
        uid = uuid.uuid4().hex[:8]
        register(base_url, f"dup_{uid}", f"dup_{uid}@example.com", "TestPass123!")
        resp = register(base_url, f"dup_{uid}", f"other_{uid}@example.com", "TestPass123!")
        record(f"username=dup_{uid} email=other_{uid}@example.com password=TestPass123!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "already taken" in resp.text.lower()

    def test_register_duplicate_email_shows_error(self, base_url):
        uid = uuid.uuid4().hex[:8]
        register(base_url, f"u1_{uid}", f"shared_{uid}@example.com", "TestPass123!")
        resp = register(base_url, f"u2_{uid}", f"shared_{uid}@example.com", "TestPass123!")
        record(f"username=u2_{uid} email=shared_{uid}@example.com password=TestPass123!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "already registered" in resp.text.lower()

    def test_register_short_password_shows_error(self, base_url):
        uid = uuid.uuid4().hex[:8]
        resp = register(base_url, f"short_{uid}", f"short_{uid}@example.com", "abc")
        record(f"username=short_{uid} password=abc", f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "8" in resp.text

    def test_register_password_mismatch_shows_error(self, base_url):
        uid = uuid.uuid4().hex[:8]
        resp = form_post(base_url, "/register", {
            "username": f"mismatch_{uid}",
            "email": f"mismatch_{uid}@example.com",
            "password": "TestPass123!",
            "confirmPassword": "Different456!",
        })
        record(f"username=mismatch_{uid} password=TestPass123! confirm=Different456!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "do not match" in resp.text.lower()


class TestLogin:

    def test_login_valid_credentials_sets_jwt_cookie(self, base_url):
        uid = uuid.uuid4().hex[:8]
        register(base_url, f"loginok_{uid}", f"loginok_{uid}@example.com", "TestPass123!")
        resp = form_post(base_url, "/login", {
            "username": f"loginok_{uid}",
            "password": "TestPass123!",
        })
        token = resp.cookies.get("jwt")
        record(f"username=loginok_{uid} password=TestPass123!",
               f"HTTP {resp.status_code}",
               f"Location={resp.headers.get('Location')} jwt={(token or '')[:24]}")
        assert resp.status_code == 302
        assert "/home" in resp.headers.get("Location", "")
        assert token is not None and len(token) > 10

    def test_login_invalid_password_returns_error(self, base_url):
        uid = uuid.uuid4().hex[:8]
        register(base_url, f"badup_{uid}", f"badup_{uid}@example.com", "TestPass123!")
        resp = form_post(base_url, "/login", {
            "username": f"badup_{uid}",
            "password": "WrongPassword!",
        })
        record(f"username=badup_{uid} password=WrongPassword!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "invalid" in resp.text.lower()

    def test_login_unknown_user_returns_error(self, base_url):
        username = "nobody_" + uuid.uuid4().hex[:8]
        resp = form_post(base_url, "/login", {
            "username": username,
            "password": "TestPass123!",
        })
        record(f"username={username} password=TestPass123!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "invalid" in resp.text.lower()

    def test_protected_route_without_token_redirects(self, base_url):
        resp = requests.get(f"{base_url}/home", allow_redirects=False)
        record("GET /home cookie=(none)", f"HTTP {resp.status_code}", f"Location={resp.headers.get('Location')}")
        assert resp.status_code == 302
        assert "login" in resp.headers.get("Location", "").lower()


class TestLogout:

    def test_logout_clears_jwt_cookie(self, base_url, auth_user):
        token, user = auth_user
        s = requests.Session()
        s.cookies.set("jwt", token)
        resp = s.post(f"{base_url}/logout", allow_redirects=False)
        jwt_cookie = resp.cookies.get("jwt")
        record(f"jwt length={len(token)}", f"HTTP {resp.status_code}",
               f"Location={resp.headers.get('Location')} set-cookie jwt={jwt_cookie!r}")
        assert resp.status_code == 302
        assert jwt_cookie is None or jwt_cookie == ""


class TestChangePassword:

    def test_change_password_success(self, base_url, auth_user):
        token, user = auth_user
        resp = form_post(base_url, "/profile/change-password", {
            "currentPassword": user["password"],
            "newPassword": "NewTestPass456!",
            "confirmPassword": "NewTestPass456!",
        }, jwt=token)
        new_token = login(base_url, user["username"], "NewTestPass456!")
        record(f"username={user['username']} current={user['password']} new=NewTestPass456!",
               f"HTTP {resp.status_code}",
               f"Location={resp.headers.get('Location')} new jwt={(new_token or '')[:24]}")
        assert resp.status_code == 302
        assert "/profile/change-password" in resp.headers.get("Location", "")
        assert new_token and len(new_token) > 10

    def test_change_password_wrong_current(self, base_url, auth_user):
        token, user = auth_user
        resp = form_post(base_url, "/profile/change-password", {
            "currentPassword": "WrongOldPassword!",
            "newPassword": "NewTestPass456!",
            "confirmPassword": "NewTestPass456!",
        }, jwt=token)
        record(f"username={user['username']} current=WrongOldPassword! new=NewTestPass456!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "incorrect" in resp.text.lower()

    def test_change_password_mismatch_shows_error(self, base_url, auth_user):
        token, user = auth_user
        resp = form_post(base_url, "/profile/change-password", {
            "currentPassword": user["password"],
            "newPassword": "NewTestPass456!",
            "confirmPassword": "DifferentPass789!",
        }, jwt=token)
        record(f"username={user['username']} new=NewTestPass456! confirm=DifferentPass789!",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "do not match" in resp.text.lower()

    def test_change_password_too_short(self, base_url, auth_user):
        token, user = auth_user
        resp = form_post(base_url, "/profile/change-password", {
            "currentPassword": user["password"],
            "newPassword": "abc",
            "confirmPassword": "abc",
        }, jwt=token)
        record(f"username={user['username']} new=abc confirm=abc",
               f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 200
        assert "8" in resp.text
