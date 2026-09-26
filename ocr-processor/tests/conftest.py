"""Root tests conftest for shared configuration and CLI options."""
import pytest


def pytest_addoption(parser):
    try:
        parser.addoption("--base-url", default="http://localhost:8080",
                         help="Base URL of the running ocr-processor app")
    except ValueError:
        pass


@pytest.fixture(scope="session")
def base_url(pytestconfig):
    return pytestconfig.getoption("--base-url")
