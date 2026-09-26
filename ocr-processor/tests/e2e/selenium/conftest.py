"""Chrome driver for the Selenium end-to-end cases."""
import sys
import uuid
from pathlib import Path

import pytest

try:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    HAS_SELENIUM = True
except Exception:
    HAS_SELENIUM = False

e2e_parent = str(Path(__file__).resolve().parents[1])
if e2e_parent not in sys.path:
    sys.path.append(e2e_parent)

from forms import register


@pytest.fixture
def driver(request):
    if not HAS_SELENIUM:
        pytest.skip("Selenium is not installed in the Python environment.")
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1280,900")
    try:
        browser = webdriver.Chrome(options=options)
    except Exception as ex:
        pytest.skip(f"Chrome WebDriver could not be initialized: {ex}")
    browser.implicitly_wait(2)
    yield browser
    _save_screenshot(browser, request.node.nodeid)
    browser.quit()


def _save_screenshot(browser, nodeid):
    parts = nodeid.split("::")
    name = parts[-1]
    if len(parts) >= 3:
        case_id = parts[-2] + "." + name
    else:
        file_name = parts[0].rsplit("/", 1)[-1].removesuffix(".py")
        case_id = file_name + "." + name
    out = Path("target/test-screenshots")
    out.mkdir(parents=True, exist_ok=True)
    try:
        browser.save_screenshot(str(out / f"{case_id}.png"))
    except Exception:
        pass


@pytest.fixture
def account(base_url):
    uid = uuid.uuid4().hex[:8]
    user = {
        "username": f"sel_{uid}",
        "email": f"sel_{uid}@example.com",
        "password": "TestPass123!",
    }
    response = register(base_url, user["username"], user["email"], user["password"])
    assert response.status_code == 302
    assert "/login" in response.headers.get("Location", "")
    return user
