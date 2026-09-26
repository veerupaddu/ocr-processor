"""Chrome driver for the Selenium end-to-end cases."""
import sys
import uuid
from pathlib import Path

import pytest
from selenium import webdriver
from selenium.webdriver.chrome.options import Options

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forms import register


@pytest.fixture
def driver(request):
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1280,900")
    browser = webdriver.Chrome(options=options)
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
    browser.save_screenshot(str(out / f"{case_id}.png"))


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
