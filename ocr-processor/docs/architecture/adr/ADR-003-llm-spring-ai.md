# ADR-003: Use Spring AI for Provider-Agnostic LLM Summarisation

> **Status:** Accepted  
> **Date:** 2026-09-27  
> **Deciders:** Engineering team

## Context

REQ-007 mandates automated summarisation of extracted OCR text. We required an integration model that supports modern foundation models (including DeepSeek and OpenAI) while isolating application services from provider-specific SDK lock-in.

## Options Considered

**Option A: Spring AI (Provider-Agnostic Abstraction)**
- Pros: Unified `ChatClient` abstraction; seamless provider switching via configuration properties; native Spring Boot auto-configuration; direct support for DeepSeek through OpenAI-compatible endpoints; supports local models (Ollama) and enterprise endpoints (watsonx)
- Cons: Relies on Spring AI ecosystem release lifecycle
- Effort: Low

**Option B: Direct Provider SDKs (OpenAI / DeepSeek SDKs)**
- Pros: Provider-specific novel parameters available immediately
- Cons: Tight architectural coupling to a single vendor; provider migrations require code refactoring
- Effort: Medium

**Option C: LangChain4j**
- Pros: Rich multi-agent tooling and chaining
- Cons: Additional heavyweight transitive dependencies for single-prompt document summarisation
- Effort: Medium

## Decision

**Option A — Spring AI**, leveraging the standard `ChatClient` with support for both **DeepSeek** and **OpenAI**:

- Configured via environment variables: `LLM_API_KEY`, `LLM_BASE_URL`, and `LLM_MODEL`
- Seamlessly connects to DeepSeek (`https://api.deepseek.com`, model `deepseek-chat`) or OpenAI (`https://api.openai.com`, model `gpt-4o-mini`) using the standard OpenAI chat protocol
- Input text is safely bounded with a 10,000-character truncation ceiling
- The application service layer interacts exclusively through the `LlmService` interface

Default summarisation prompt:
```
Summarise the following document text in 3–5 sentences. Be concise and factual.

{extractedText}
```

## Consequences

### Positive
- Provider flexibility: Switching between DeepSeek, OpenAI, Anthropic, or local Ollama instances is accomplished entirely via configuration
- High testability: Mocking `LlmService` in unit tests enables reliable verification of business logic without external API costs
- Robust resilience: Network and token issues trigger graceful degradation with `summaryStatus = FAILED` and one-click retry capabilities

### Negative
- Character truncation at 10,000 characters prioritizes prompt bounds over full document ingestion

### Neutral
- Temperature and sampling parameters are exposed in `application.yml` for domain fine-tuning
