# ADR-004: Store JWT in HTTP-only cookie, not Authorization header

> **Status:** Accepted  
> **Date:** 2025-07-14  
> **Deciders:** Engineering team

## Context

REQ-002 requires JWT-based authentication. A key security decision is where the JWT is stored on the client side. The two common approaches are:

1. **Authorization header** — frontend JavaScript reads the token from `localStorage` or `sessionStorage` and sets `Authorization: Bearer <token>` on each request.
2. **HTTP-only cookie** — the server sets `Set-Cookie: jwt=<token>; HttpOnly; Secure; SameSite=Strict`; the browser sends it automatically.

## Options Considered

**Option A: HTTP-only cookie**
- Pros: Token is inaccessible to JavaScript → XSS cannot steal the token; browser sends cookie automatically → no frontend token management code; natural fit for server-rendered Thymeleaf app
- Cons: Requires CSRF protection for state-changing requests (mitigated by SameSite=Strict); slightly more complex logout (server must clear cookie)
- Effort: Low

**Option B: localStorage + Authorization header**
- Pros: Simple for SPA clients; easy to inspect in DevTools during development
- Cons: XSS vulnerability — any injected script can read `localStorage` and exfiltrate the token; not recommended by OWASP for sensitive applications
- Effort: Low

**Option C: sessionStorage + Authorization header**
- Pros: Token cleared on tab close
- Cons: Same XSS exposure as Option B; lost between tabs
- Effort: Low

## Decision

**Option A — HTTP-only cookie** with the following attributes:
- `HttpOnly` — inaccessible to JavaScript
- `Secure` — HTTPS only (dev profile exempts this)
- `SameSite=Strict` — prevents CSRF from cross-site requests
- Expiry: configurable, default 8 hours

CSRF tokens are enabled for HTML form submissions (Spring Security default). REST endpoints (`/api/**`) exempt from CSRF (cookie `SameSite=Strict` provides equivalent protection).

## Consequences

### Positive
- XSS cannot exfiltrate the JWT
- No frontend token-management code needed
- Spring Security cookie-based JWT filter is straightforward to implement

### Negative
- Logout requires an explicit `POST /logout` endpoint that clears the cookie (already in REQ-002-08)
- Mobile/native clients would need a different auth strategy (out of scope for v1)

### Neutral
- JWT payload is still readable (base64); sensitive data (password hash etc.) must never be included in claims
