"""
test_search.py — E2E tests for the search endpoint.
"""
import io
import pytest

from evidence import record


class TestSearch:

    def _upload(self, session, base_url, filename, document_name):
        resp = session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": (filename, io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
            data={"documentName": document_name},
        )
        assert resp.status_code == 201
        return resp.json()["id"]

    def test_empty_query_returns_all_user_records(self, base_url, authed_session):
        self._upload(authed_session, base_url, "a.png", "Alpha Document")
        self._upload(authed_session, base_url, "b.png", "Beta Document")

        resp = authed_session.get(f"{base_url}/api/v1/ocr/search",
                                   params={"q": "", "page": 0, "size": 20})
        body = resp.json()
        record("q=\"\" page=0 size=20 documents=Alpha Document, Beta Document",
               f"HTTP {resp.status_code}",
               f"totalElements={body.get('totalElements')} names={[item.get('documentName') for item in body.get('content', [])]}")
        assert resp.status_code == 200
        assert body["totalElements"] >= 2

    def test_empty_query_result_has_required_fields(self, base_url, authed_session):
        self._upload(authed_session, base_url, "c.png", "Gamma Document")

        resp = authed_session.get(f"{base_url}/api/v1/ocr/search", params={"q": ""})
        assert resp.status_code == 200
        body = resp.json()
        assert "content" in body
        assert "totalElements" in body
        assert "totalPages" in body
        assert "number" in body
        assert body["content"], "search returned no rows to inspect"
        item = body["content"][0]
        record("q=\"\" documentName=\"Gamma Document\"",
               f"HTTP {resp.status_code} page={body['number']} totalPages={body['totalPages']}",
               f"documentName={item.get('documentName')!r} status={item.get('status')!r} id={item.get('id')}")
        assert "id" in item
        assert "documentName" in item
        assert "status" in item
        assert "createdAt" in item

    def test_no_match_returns_empty_content(self, base_url, authed_session):
        resp = authed_session.get(f"{base_url}/api/v1/ocr/search",
                                   params={"q": "xyzzy_unique_term_that_wont_match_anything"})
        body = resp.json()
        record("q=\"xyzzy_unique_term_that_wont_match_anything\"",
               f"HTTP {resp.status_code}", f"totalElements={body.get('totalElements')} content={body.get('content')}")
        assert resp.status_code == 200
        assert body["totalElements"] == 0
        assert body["content"] == []

    def test_pagination_size_respected(self, base_url, authed_session):
        for i in range(4):
            self._upload(authed_session, base_url, f"pg{i}.png", f"Page Doc {i}")

        resp = authed_session.get(f"{base_url}/api/v1/ocr/search",
                                   params={"q": "", "page": 0, "size": 2})
        body = resp.json()
        record("q=\"\" page=0 size=2 documents=Page Doc 0..3",
               f"HTTP {resp.status_code}",
               f"content={len(body.get('content', []))} totalElements={body.get('totalElements')} names={[item.get('documentName') for item in body.get('content', [])]}")
        assert resp.status_code == 200
        assert len(body["content"]) == 2
        assert body["totalElements"] >= 4

    def test_search_without_auth_rejected(self, base_url):
        import requests
        resp = requests.get(f"{base_url}/api/v1/ocr/search",
                            params={"q": ""}, allow_redirects=False)
        record("GET /api/v1/ocr/search cookie=(none)", f"HTTP {resp.status_code}", f"Location={resp.headers.get('Location')}")
        assert resp.status_code == 302
        assert "login" in resp.headers.get("Location", "").lower()

    def test_results_scoped_to_current_user(self, base_url, authed_session, auth_user):
        """Search must not return records belonging to other users."""
        import uuid, requests
        from forms import register, login

        # Upload as the authed_session user
        self._upload(authed_session, base_url, "scoped.png", "Scoped Document")

        # Create a second user and search — should see 0 results
        uid = uuid.uuid4().hex[:8]
        register(base_url, f"scope2_{uid}", f"scope2_{uid}@example.com", "TestPass123!")
        token2 = login(base_url, f"scope2_{uid}", "TestPass123!")
        s2 = requests.Session()
        s2.cookies.set("jwt", token2)

        resp = s2.get(f"{base_url}/api/v1/ocr/search", params={"q": ""})
        body = resp.json()
        record(f"owner uploaded \"Scoped Document\"; searched as scope2_{uid}",
               f"HTTP {resp.status_code}", f"totalElements={body.get('totalElements')} content={body.get('content')}")
        assert resp.status_code == 200
        assert body["totalElements"] == 0
