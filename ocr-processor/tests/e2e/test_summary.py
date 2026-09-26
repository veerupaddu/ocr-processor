"""
test_summary.py — E2E tests for LLM summarisation and retry.
"""
import io
import pytest

from evidence import record, visible


class TestSummary:

    def _upload(self, session, base_url):
        resp = session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("summary_test.png",
                   io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
        )
        assert resp.status_code == 201
        return resp.json()

    def test_summary_field_present_in_upload_response(self, base_url, authed_session):
        """Upload response must contain summaryStatus field."""
        body = self._upload(authed_session, base_url)
        record("file=summary_test.png", f"HTTP 201 record {body.get('status')}",
               f"id={body.get('id')} summaryStatus={body.get('summaryStatus')!r} summary={body.get('summary')!r}")
        assert "summary" in body
        assert body["summaryStatus"] in ("COMPLETE", "FAILED", "SKIPPED", "PENDING")

    def test_summary_field_present_in_detail(self, base_url, authed_session):
        """Detail endpoint must include summaryStatus."""
        body = self._upload(authed_session, base_url)
        detail = authed_session.get(f"{base_url}/api/v1/ocr/{body['id']}")
        detail_body = detail.json()
        record(f"id={body['id']}", f"HTTP {detail.status_code}",
               f"summaryStatus={detail_body.get('summaryStatus')!r} summary={detail_body.get('summary')!r} extractedText={detail_body.get('extractedText')!r}")
        assert detail.status_code == 200
        assert "summary" in detail_body
        assert detail_body["summaryStatus"] in ("COMPLETE", "FAILED", "SKIPPED", "PENDING")

    def test_retry_summary_on_non_failed_returns_409(self, base_url, authed_session):
        """Retry is only allowed when summaryStatus == FAILED."""
        body = self._upload(authed_session, base_url)
        record_id = body["id"]
        assert body["summaryStatus"] != "FAILED", body
        resp = authed_session.post(f"{base_url}/api/v1/ocr/{record_id}/retry-summary")
        message = resp.json().get("message") if resp.headers.get("content-type", "").startswith("application/json") else resp.text[:160]
        record(f"id={record_id} summaryStatus={body['summaryStatus']}", f"HTTP {resp.status_code}", f"message={message!r}")
        assert resp.status_code == 409
        assert "FAILED" in resp.text

    def test_retry_summary_unknown_record_returns_404(self, base_url, authed_session):
        resp = authed_session.post(
            f"{base_url}/api/v1/ocr/00000000-0000-0000-0000-000000000000/retry-summary")
        record("id=00000000-0000-0000-0000-000000000000", f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 404

    def test_retry_summary_other_users_record_returns_404(self, base_url, auth_user):
        """User B cannot retry summary on User A's record."""
        import uuid, requests, io
        from forms import register, login

        token_a, _ = auth_user
        s_a = requests.Session()
        s_a.cookies.set("jwt", token_a)
        resp = s_a.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("priv.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
        )
        assert resp.status_code == 201
        record_id = resp.json()["id"]

        uid = uuid.uuid4().hex[:8]
        register(base_url, f"retryb_{uid}", f"retryb_{uid}@example.com", "TestPass123!")
        token_b = login(base_url, f"retryb_{uid}", "TestPass123!")
        s_b = requests.Session()
        s_b.cookies.set("jwt", token_b)
        resp_b = s_b.post(f"{base_url}/api/v1/ocr/{record_id}/retry-summary")
        record(f"owner A id={record_id} retried as retryb_{uid}", f"HTTP {resp_b.status_code}", visible(resp_b.text))
        assert resp_b.status_code == 404
