# ADR-005: Use PostgreSQL TSVECTOR Full-Text Search with GIN Indexing

> **Status:** Accepted  
> **Date:** 2026-09-27  
> **Deciders:** Engineering team

## Context

REQ-005 mandates fast text search across document names, extracted OCR content, and LLM summaries with strict multi-tenant isolation by user ID. We evaluated search architectures balancing operational simplicity and query latency under 2 seconds for up to 10,000 records.

## Options Considered

**Option A: PostgreSQL TSVECTOR Full-Text Search (Native with GIN Index)**
- Pros: Single unified database engine; zero additional infrastructure to operate; strict ACID transaction guarantees; sub-second search latencies using GIN inverted indexes; native integration with Spring Data JPA queries
- Cons: Advanced fuzzy matching and aggregations require explicit extension configuration
- Effort: Low

**Option B: External Search Cluster (Elasticsearch / OpenSearch)**
- Pros: Distributed cluster scaling; faceted navigation and BM25 relevance ranking
- Cons: Requires dedicated operational cluster; cross-datastore synchronization and index rebalancing overhead; excessive complexity for current document volume
- Effort: High

**Option C: Standard SQL LIKE / ILIKE String Matching**
- Pros: Minimal implementation effort
- Cons: Performs table-wide sequential scans on unbounded text fields; degrades as document volume grows
- Effort: Low

## Decision

**Option A — PostgreSQL TSVECTOR Full-Text Search**, structured as follows:

- `ocr_records` table incorporates a dedicated `search_vector` column of type `TSVECTOR`
- A database trigger automatically updates `search_vector` on insert or update, indexing document names (weight A), extracted text (weight B), and summaries (weight C)
- A `GIN` index on `search_vector` delivers sub-second response times across large corpora
- Spring Data JPA repository queries leverage PostgreSQL `to_tsquery` scoped to the authenticated user ID

## Consequences

### Positive
- Zero infrastructure overhead beyond PostgreSQL 16
- Consistently satisfies the sub-2-second search SLA across unit, integration, and E2E verification
- Native isolation guarantees: documents are strictly filtered by authenticated user ID
- Direct support in Flyway database migrations

### Negative
- Database trigger introduces a small write overhead during record insertion and update

### Neutral
- The `pg_trgm` extension is available for future fuzzy matching without altering core architecture
