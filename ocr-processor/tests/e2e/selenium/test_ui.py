"""Browser journeys against the running ocr-processor app."""
import uuid
from pathlib import Path

from evidence import record
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

HELLO_PNG = Path(__file__).parent / "fixtures" / "hello.png"


def test_home_without_login_redirects(driver, base_url):
    driver.get(base_url + "/home")
    WebDriverWait(driver, 10).until(EC.url_contains("/login"))
    assert driver.find_element(By.CSS_SELECTOR, "form[action='/login'], form[action*='login']")
    record("GET /home cookie=(none)", "redirected", f"url={driver.current_url}")


def test_register_reaches_login(driver, base_url):
    uid = uuid.uuid4().hex[:8]
    driver.get(base_url + "/register")
    driver.find_element(By.ID, "username").send_keys(f"ui_{uid}")
    driver.find_element(By.ID, "email").send_keys(f"ui_{uid}@example.com")
    driver.find_element(By.ID, "password").send_keys("TestPass123!")
    driver.find_element(By.ID, "confirmPassword").send_keys("TestPass123!")
    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
    WebDriverWait(driver, 10).until(EC.url_contains("/login"))
    record(f"username=ui_{uid} email=ui_{uid}@example.com password=TestPass123!",
           "form submitted", f"url={driver.current_url}")


def test_login_invalid_password_shows_error(driver, base_url, account):
    _login(driver, base_url, account["username"], "WrongPass123!")
    error = WebDriverWait(driver, 10).until(
        EC.visibility_of_element_located((By.CSS_SELECTOR, ".alert-error"))
    )
    assert error.text.strip()
    assert "/login" in driver.current_url
    record(f"username={account['username']} password=WrongPass123!",
           f"url={driver.current_url}", f"error={error.text.strip()!r}")


def test_login_opens_home_tabs(driver, base_url, account):
    _login(driver, base_url, account["username"], account["password"])
    WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.CSS_SELECTOR, "[data-tab='search']")))
    labels = [button.text for button in driver.find_elements(By.CSS_SELECTOR, ".tab-btn")]
    assert any("Search" in label for label in labels)
    assert any("Create" in label for label in labels)
    assert any("Tests" in label for label in labels)
    record(f"username={account['username']} password={account['password']}",
           f"url={driver.current_url}", "tabs=" + ", ".join(label.strip() for label in labels))


def test_upload_png_shows_extraction(driver, base_url, account):
    _login(driver, base_url, account["username"], account["password"])
    driver.find_element(By.CSS_SELECTOR, "[data-tab='create']").click()
    file_input = driver.find_element(By.ID, "fileInput")
    driver.execute_script("arguments[0].style.display='block'", file_input)
    file_input.send_keys(str(HELLO_PNG.resolve()))
    WebDriverWait(driver, 10).until(lambda d: d.find_element(By.ID, "uploadBtn").is_enabled())
    driver.find_element(By.ID, "uploadBtn").click()
    result = WebDriverWait(driver, 90).until(
        EC.visibility_of_element_located((By.ID, "upload-result"))
    )
    assert "COMPLETE" in result.text
    assert "HELLO" in result.text.upper()
    record("file=hello.png tab=Create", "upload finished", " ".join(result.text.split())[:180])


def test_search_panel_returns_results(driver, base_url, account):
    _login(driver, base_url, account["username"], account["password"])
    driver.find_element(By.CSS_SELECTOR, "#search-panel button").click()
    WebDriverWait(driver, 10).until(
        lambda d: d.find_elements(By.CSS_SELECTOR, "#search-results .result-card, #search-results .empty-state")
    )
    cards = driver.find_elements(By.CSS_SELECTOR, "#search-results .result-card")
    empty = driver.find_elements(By.CSS_SELECTOR, "#search-results .empty-state")
    record(f"username={account['username']} q=(empty)", "search rendered",
           f"cards={len(cards)} emptyStates={len(empty)}")


def test_logout_returns_to_login(driver, base_url, account):
    _login(driver, base_url, account["username"], account["password"])
    WebDriverWait(driver, 10).until(EC.element_to_be_clickable((By.CSS_SELECTOR, "button.btn-link"))).click()
    WebDriverWait(driver, 10).until(EC.url_contains("/login"))
    record(f"username={account['username']} clicked Logout", "redirected", f"url={driver.current_url}")


def _login(driver, base_url, username, password):
    driver.get(base_url + "/login")
    driver.find_element(By.ID, "username").send_keys(username)
    driver.find_element(By.ID, "password").send_keys(password)
    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
