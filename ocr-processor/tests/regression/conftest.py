"""Regression suite configuration and fixtures."""
import pytest


def pytest_addoption(parser):
    try:
        parser.addoption("--base-url", default="http://localhost:8080",
                         help="Base URL of the running ocr-processor app")
    except ValueError:
        pass


@pytest.fixture(scope="module")
def base_url(pytestconfig):
    return pytestconfig.getoption("--base-url")
