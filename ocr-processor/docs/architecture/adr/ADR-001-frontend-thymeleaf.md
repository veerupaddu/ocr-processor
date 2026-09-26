# ADR-001: Use Thymeleaf (server-rendered) over SPA for v1

> **Status:** Accepted  
> **Date:** 2025-07-14  
> **Deciders:** Engineering team

## Context

The requirements call for a login page, registration screen, change password screen, and a home page with Search/Create tabs. We need to decide whether to build the frontend as a separate Single Page Application (React/Vue) or use a server-rendered template engine bundled inside the Spring Boot app.

## Options Considered

**Option A: Thymeleaf (server-rendered)**
- Pros: Single deployable JAR; no separate frontend build pipeline; no CORS configuration; Spring Security integrates natively; faster time-to-working-app
- Cons: Less interactive UI; tab switching requires JavaScript fragment swapping or HTMX; harder to evolve to a rich UI later without rewrite
- Effort: Low

**Option B: React SPA + Spring Boot REST API**
- Pros: Rich interactive UI; clear frontend/backend separation; easier to evolve UI independently
- Cons: Two deployables to manage; CORS setup required; JWT handling more complex (SPA cannot use HTTP-only cookies easily); separate build pipeline; higher setup effort
- Effort: High

**Option C: HTMX + Thymeleaf**
- Pros: Server-rendered with partial page updates; no full JS framework; HTTP-only JWT cookies work naturally
- Cons: Less mainstream; smaller community
- Effort: Low–Medium

## Decision

**Option A — Thymeleaf**, with lightweight vanilla JavaScript for tab switching and AJAX file upload progress.

HTMX (Option C) is a valid future upgrade path and will be noted in the design document.

## Consequences

### Positive
- Single JAR deployment; simpler CI/CD pipeline for v1
- Spring Security CSRF and session management work out of the box
- HTTP-only JWT cookie approach is natural with server-rendered pages

### Negative
- Tab switching and upload progress feedback require custom JavaScript
- If a rich SPA is required in v2, a partial rewrite of the frontend is needed

### Neutral
- REST API endpoints are still implemented cleanly, so a future SPA migration only replaces the Thymeleaf templates
