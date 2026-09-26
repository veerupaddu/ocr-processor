# ADR-002: Use Tesseract + Apache Tika over cloud OCR API

> **Status:** Accepted  
> **Date:** 2025-07-14  
> **Deciders:** Engineering team

## Context

OCR extraction is the core feature of the application. We need to choose between an open-source, self-hosted OCR engine and a cloud-based OCR API.

Supported file types: PDF, PNG, JPG, JPEG, TIFF (up to 20 MB).

## Options Considered

**Option A: Tesseract 4 + Apache Tika (open-source, embedded)**
- Pros: No per-call cost; no external dependency at runtime; data stays on-premises (important for OQ-4 compliance); works offline; `tess4j` (Java wrapper) is mature
- Cons: Lower accuracy than cloud APIs on complex layouts; requires Tesseract binary installed in the deployment environment; PDF handling requires PDFBox or Tika
- Effort: Medium (Docker base image must include Tesseract)

**Option B: Google Cloud Vision API**
- Pros: Very high accuracy; handles complex layouts, tables, handwriting; managed service
- Cons: Per-call cost ($1.50/1000 pages); sends document content to Google (data residency risk); requires Google Cloud account and API key; network latency per request
- Effort: Low (REST API call)

**Option C: AWS Textract**
- Pros: High accuracy; structured data extraction (tables, forms); AWS-native
- Cons: Per-page cost; AWS vendor lock-in; data leaves the deployment environment
- Effort: Low (SDK call)

## Decision

**Option A — Tesseract 4 + Apache Tika.**

Apache Tika handles PDF → image conversion and multi-format file parsing. Tess4j wraps Tesseract 4 for Java. The Dockerfile will include `apt-get install tesseract-ocr`.

If accuracy requirements change after v1, the `OcrEngine` interface allows swapping the implementation without changing the service layer (Strategy pattern).

## Consequences

### Positive
- Zero per-call cost; suitable for high-volume usage
- Document content does not leave the deployment environment (addresses OQ-4)
- `OcrEngine` interface makes the provider swappable

### Negative
- Accuracy on low-quality scans or complex layouts may be lower than cloud APIs
- Tesseract binary must be present in the Docker image (adds ~100 MB to image size)
- Language packs must be pre-installed (English only for v1)

### Neutral
- PDFBox is already a Tika dependency — no extra library needed for PDF support
