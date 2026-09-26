"""
test_ocr.py — E2E tests for OCR upload, extraction, and record lifecycle.
"""
import io
import pytest
import requests

from evidence import record, visible


class TestOcrUpload:

    def test_upload_png_returns_created(self, base_url, authed_session):
        """Upload a minimal PNG bytes payload; expect 201 with status field."""
        resp = authed_session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("test.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
            data={"documentName": "E2E Test Image"},
        )
        body = resp.json()
        record("file=test.png documentName=\"E2E Test Image\"",
               f"HTTP {resp.status_code} record {body.get('status')}",
               f"id={body.get('id')} extractedText={body.get('extractedText')!r} summary={body.get('summary')!r}")
        assert resp.status_code == 201
        assert body["id"]
        assert body["status"] in ("COMPLETE", "FAILED")

    def test_upload_without_auth_rejected(self, base_url):
        """Unauthenticated upload must be rejected."""
        resp = requests.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("test.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
            allow_redirects=False,
        )
        record("file=test.png cookie=(none)", f"HTTP {resp.status_code}", f"Location={resp.headers.get('Location')}")
        assert resp.status_code == 302
        assert "login" in resp.headers.get("Location", "").lower()

    def test_upload_defaults_document_name_to_filename(self, base_url, authed_session):
        """If documentName is omitted, the original filename is used."""
        resp = authed_session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("myfile.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
        )
        body = resp.json()
        record("file=myfile.png documentName=(omitted)", f"HTTP {resp.status_code}",
               f"documentName={body.get('documentName')!r} originalFilename={body.get('originalFilename')!r}")
        assert resp.status_code == 201
        assert body["documentName"] == "myfile.png"

    def test_upload_unsupported_type_returns_error(self, base_url, authed_session):
        """Uploading a .docx should be rejected."""
        resp = authed_session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("doc.docx", io.BytesIO(b"PK\x03\x04" + b"\x00" * 100),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
        )
        message = resp.json().get("message") if resp.headers.get("content-type", "").startswith("application/json") else resp.text[:120]
        record("file=doc.docx", f"HTTP {resp.status_code}", f"message={message!r}")
        assert resp.status_code == 400
        assert "Unsupported" in resp.text

    def test_upload_record_has_expected_fields(self, base_url, authed_session):
        """Response must include all required fields."""
        resp = authed_session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("fields.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
            data={"documentName": "Fields Test"},
        )
        body = resp.json()
        required = {"id", "documentName", "originalFilename", "fileType",
                    "fileSizeBytes", "status", "summaryStatus", "createdAt"}
        record("file=fields.png documentName=\"Fields Test\"", f"HTTP {resp.status_code} record {body.get('status')}",
               "fields=" + ", ".join(f"{key}={body.get(key)!r}" for key in sorted(required)))
        assert resp.status_code == 201
        assert required.issubset(body.keys())

    def test_uploaded_record_is_retrievable(self, base_url, authed_session):
        """After upload, GET /api/v1/ocr/{id} returns the same record."""
        resp = authed_session.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("retrieve.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
            data={"documentName": "Retrieve Test"},
        )
        assert resp.status_code == 201
        record_id = resp.json()["id"]

        detail = authed_session.get(f"{base_url}/api/v1/ocr/{record_id}")
        detail_body = detail.json()
        record(f"id={record_id} documentName=\"Retrieve Test\"", f"HTTP {detail.status_code}",
               f"id={detail_body.get('id')} documentName={detail_body.get('documentName')!r} status={detail_body.get('status')}")
        assert detail.status_code == 200
        assert detail_body["id"] == record_id
        assert detail_body["documentName"] == "Retrieve Test"


class TestOcrDetail:

    def test_get_nonexistent_record_returns_404(self, base_url, authed_session):
        resp = authed_session.get(f"{base_url}/api/v1/ocr/00000000-0000-0000-0000-000000000000")
        record("id=00000000-0000-0000-0000-000000000000", f"HTTP {resp.status_code}", visible(resp.text))
        assert resp.status_code == 404

    def test_other_users_record_not_accessible(self, base_url, auth_user):
        """User A's record must not be accessible by User B."""
        import uuid
        from forms import register, login

        # Upload as user A
        token_a, _ = auth_user
        s_a = requests.Session()
        s_a.cookies.set("jwt", token_a)
        resp = s_a.post(f"{base_url}/api/v1/ocr/upload",
            files={"file": ("private.png", io.BytesIO(b"\x89PNG\r\n" + b"\x00" * 100), "image/png")},
        )
        assert resp.status_code == 201
        record_id = resp.json()["id"]

        # Try to access as user B
        uid = uuid.uuid4().hex[:8]
        register(base_url, f"userb_{uid}", f"userb_{uid}@example.com", "TestPass123!")
        token_b = login(base_url, f"userb_{uid}", "TestPass123!")
        s_b = requests.Session()
        s_b.cookies.set("jwt", token_b)
        resp_b = s_b.get(f"{base_url}/api/v1/ocr/{record_id}")
        record(f"owner A id={record_id} requested as userb_{uid}", f"HTTP {resp_b.status_code}", visible(resp_b.text))
        assert resp_b.status_code == 404
