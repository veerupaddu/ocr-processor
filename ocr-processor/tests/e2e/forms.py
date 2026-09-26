"""Form posts that carry the CSRF token issued with the page."""
import re

import requests


def _token(html):
    match = re.search(r'name="_csrf" value="([^"]+)"', html)
    if not match:
        raise AssertionError("CSRF token was not on the form")
    return match.group(1)


def form_post(base_url, path, data, jwt=None):
    session = requests.Session()
    if jwt:
        session.cookies.set("jwt", jwt)
    page = session.get(f"{base_url}{path}")
    payload = dict(data)
    payload["_csrf"] = _token(page.text)
    return session.post(f"{base_url}{path}", data=payload, allow_redirects=False)


def register(base_url, username, email, password):
    return form_post(base_url, "/register", {
        "username": username,
        "email": email,
        "password": password,
        "confirmPassword": password,
    })


def login(base_url, username, password):
    response = form_post(base_url, "/login", {
        "username": username,
        "password": password,
    })
    return response.cookies.get("jwt")
