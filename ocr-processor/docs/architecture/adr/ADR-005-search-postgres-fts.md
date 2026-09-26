# ADR-005: Use PostgreSQL full-text search over Elasticsearch

> **Status:** Accepted  
> **Date:** 2025-07-14  
> **Deciders:** Engineering team

## Context

REQ-005 requires searching OCR records by document name, extracted text, and LLM summary. At scale, full-text search typically uses dedicated engines (Elasticsearch, OpenSearch). For v1 we must choose between adding a dedicated search service or leveraging PostgreSQL's built-in full-text search.

## Options Considered

**Option A: PostgreSQL full-text search (tsvector + GIN index)**
- Pros: No additional infrastructure; single data store; ACID guarantees; sufficient for datasets up to ~1M rows; GIN index keeps search fast; natively integrated with Spring Data JPA via `@Query`
- Cons: Less powerful than Elasticsearch for fuzzy matching, facets, and advanced relevance ranking; `tsvector` must be maintained (trigger or application-level)
- Effort: Low

**Option B: Elasticsearch / OpenSearch**
- Pros: Industry-leading full-text search; fuzzy matching; faceted search; horizontal scaling
- Cons: Separate service to deploy, monitor, and keep in sync with PostgreSQL; significant operational overhead for v1; overkill for the expected dataset size
- Effort: High

**Option C: LIKE / ILIKE queries**
- Pros: Trivial to implement
- Cons: No index support on large TEXT columns; poor performance at scale; no relevance ranking
- Effort: Very Low (but not acceptable beyond small datasets)

## Decision

**Option A — PostgreSQL full-text search.**

Implementation:
- `ocr_records` table gains a `search_vector` column (`tsvector`)
- A PostgreSQL `BEFORE INSERT OR UPDATE` trigger maintains `search_vector` from `document_name`, `extracted_text`, and `summary`
- A `GIN` index on `search_vector` ensures sub-second search up to 10,000 records (NFR target)
- Spring Data JPA `@Query` with `to_tsquery` used in `OcrRecordRepository`

If the dataset grows beyond ~500,000 records or advanced search features are needed, migrating to Elasticsearch is an identified upgrade path — the `OcrRecordRepository` interface isolates the search implementation.

## Consequences

### Positive
- No additional infrastructure in v1
- GIN index meets the 2-second SLA for up to 10,000 records (and well beyond)
- Spring Data JPA native queries can use PostgreSQL FTS syntax directly

### Negative
- `tsvector` trigger adds a small write overhead on insert/update
- Fuzzy matching (typos) is not supported without `pg_trgm` extension (can be added later)
- Relevance ranking is basic (ts_rank) compared to Elasticsearch BM25

### Neutral
- `pg_trgm` extension (trigram similarity) can be enabled later for fuzzy search without an architecture change
