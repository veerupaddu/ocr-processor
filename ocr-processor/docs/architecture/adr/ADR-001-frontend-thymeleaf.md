# ADR-001: Use Thymeleaf Server-Rendered Architecture for Web Interface

> **Status:** Accepted  
> **Date:** 2026-09-27  
> **Deciders:** Engineering team

## Context

The system requirements specify authentication screens (login, registration, password modification) and an interactive home portal providing Search, Create, and automated Tests tabs. We evaluated frontend delivery models to balance development velocity, security guarantees, and deployment simplicity.

## Options Considered

**Option A: Thymeleaf (Server-Rendered Templates)**
- Pros: Single deployable JAR package; unified CI/CD build lifecycle; native Spring Security integration; streamlined HTTP-only cookie authentication; rapid delivery
- Cons: Rich client-side state interactions require targeted vanilla JavaScript or HTMX
- Effort: Low

**Option B: React SPA with Independent REST Backend**
- Pros: Rich client-side component state management; independent UI evolution
- Cons: Separate deployment artifacts; additional CORS configurations; increased authentication complexity with HTTP-only cookies; separate build pipelines
- Effort: High

**Option C: HTMX with Server Templates**
- Pros: Partial HTML fragment swaps; maintains server-rendered security semantics
- Cons: Smaller enterprise ecosystem compared to standard Thymeleaf patterns
- Effort: Low–Medium

## Decision

**Option A — Thymeleaf Server-Rendered Templates**, complemented with modular vanilla JavaScript for tab navigation, asynchronous upload streaming, and live Test Console polling.

## Consequences

### Positive
- Single JAR deployment with automated container packaging
- Spring Security CSRF protections and authentication context work out of the box
- HTTP-only JWT cookies pair seamlessly with browser form submissions and AJAX calls
- Fast first-contentful-paint across all views

### Negative
- Client tab state transitions require explicit JavaScript event handling

### Neutral
- REST endpoints are implemented independently, enabling future client extensions without backend refactoring
