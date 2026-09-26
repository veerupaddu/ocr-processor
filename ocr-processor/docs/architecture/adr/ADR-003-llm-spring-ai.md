# ADR-003: Use Spring AI with OpenAI adapter for LLM integration

> **Status:** Accepted  
> **Date:** 2025-07-14  
> **Deciders:** Engineering team

## Context

REQ-007 requires LLM summarisation of extracted OCR text. We need to choose an integration approach and a default provider. OQ-3 asks: OpenAI, Anthropic, or IBM watsonx?

The system must be configurable so that the LLM provider can be changed without code changes.

## Options Considered

**Option A: Spring AI (provider-agnostic abstraction)**
- Pros: Single `ChatClient` abstraction; swap provider by changing `application.yml` and dependency; actively maintained by Spring team; supports OpenAI, Anthropic, Ollama, watsonx, and others
- Cons: Relatively new library (1.0.x); may lag behind provider-specific SDKs on cutting-edge features
- Effort: Low

**Option B: OpenAI Java SDK (direct)**
- Pros: Latest OpenAI features immediately available; well-documented
- Cons: Tight coupling to OpenAI; switching providers requires code changes
- Effort: Low (but future switching effort is High)

**Option C: LangChain4j**
- Pros: Feature-rich; many providers; agent/tool support
- Cons: Heavier dependency; more complex than needed for simple summarisation
- Effort: Medium

## Decision

**Option A — Spring AI** with the **OpenAI adapter** (`gpt-4o-mini`) as the default provider.

- API key via `LLM_API_KEY` environment variable
- Prompt template configurable in `application.yml`
- The `LlmService` interface hides Spring AI; swapping providers (e.g. to IBM watsonx) is a configuration-only change

Default prompt:
```
Summarise the following document text in 3–5 sentences. Be concise and factual.

{extractedText}
```

## Consequences

### Positive
- Provider-agnostic: IBM watsonx, Anthropic, or Ollama (local) can be enabled with a config change
- Clean abstraction via `LlmService` interface keeps controllers and services unaware of the provider
- Spring Boot auto-configuration reduces boilerplate

### Negative
- Spring AI 1.0.x is newer; some providers may have partial support
- Token limits and pricing vary per provider; truncation at 10,000 characters is a conservative safety measure

### Neutral
- Model name and temperature are configurable; defaults optimised for summarisation (temperature 0.3)
