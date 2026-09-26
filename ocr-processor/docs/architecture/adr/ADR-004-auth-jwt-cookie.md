# ADR-004: Store JWT in HTTP-Only Cookie

> **Status:** Accepted  
> **Date:** 2026-09-27  
> **Deciders:** Engineering team

## Context

REQ-002 mandates secure, stateless authentication using JSON Web Tokens (JWT). Client-side credential storage represents a vital security boundary. We evaluated token storage mechanisms to defend against Cross-Site Scripting (XSS) and Cross-Site Request Forgery (CSRF).

## Options Considered

**Option A: HTTP-Only Cookie**
- Pros: Token remains inaccessible to client JavaScript, mitigating XSS token exfiltration; browser automatically attaches the cookie to requests; integrates seamlessly with server-rendered Thymeleaf forms and fetch calls
- Cons: Requires SameSite configuration and explicit server cookie invalidation on logout
- Effort: Low

**Option B: Web Storage (localStorage / sessionStorage) with Authorization Header**
- Pros: Conventional pattern for decoupled client applications
- Cons: Susceptible to script-based exfiltration during XSS incidents; requires client JavaScript token storage logic
- Effort: Low

## Decision

**Option A — HTTP-Only Cookie** with the following security attributes:
- `HttpOnly`: restricts JavaScript access to the cookie
- `Secure`: enforces transmission over HTTPS in production
- `SameSite=Strict`: isolates cookies from cross-site contexts
- Lifetime: configurable duration (default 8 hours)

The authentication token encapsulates user identification, username claims (rendered directly in the home navigation bar), and expiration timestamps.

## Consequences

### Positive
- Robust defense against script-based token theft
- Browser-native cookie lifecycle management without client storage logic
- Seamless alignment with Spring Security `JwtAuthFilter`
- Automated test coverage in `AuthControllerIT` and Selenium E2E journeys

### Negative
- Session termination requires a dedicated `POST /logout` clearing handler

### Neutral
- JWT payload contents are signed with HMAC-SHA256 using the configured secret key
