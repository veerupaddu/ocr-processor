# ADR-002: Use Embedded Tesseract and Apache Tika for Optical Character Recognition

> **Status:** Accepted  
> **Date:** 2026-09-27  
> **Deciders:** Engineering team

## Context

Document text extraction constitutes the primary pipeline capability. We evaluated open-source self-hosted OCR engines alongside commercial cloud APIs for processing multi-format inputs (PDF, PNG, JPG, JPEG, TIFF up to 20 MB).

## Options Considered

**Option A: Embedded Tesseract CLI + Apache Tika**
- Pros: Zero per-call execution cost; complete on-premises data residency; fully operational in air-gapped environments; mature ecosystem for Linux and macOS environments
- Cons: System runtime requires Tesseract binary installation
- Effort: Medium

**Option B: Google Cloud Vision API**
- Pros: High extraction fidelity across degraded scans and handwritten text
- Cons: Recurring usage fees per thousand pages; requires external network connectivity and third-party credentials; transfers document data off-premises
- Effort: Low

**Option C: AWS Textract**
- Pros: Structured table and form key-value extraction
- Cons: Per-page pricing; platform lock-in; requires cloud data egress
- Effort: Low

## Decision

**Option A — Tesseract 5 with Apache Tika.**

Apache Tika manages multi-format MIME detection, PDF page splitting, and metadata extraction. The `TesseractOcrEngine` implementation invokes the native Tesseract engine with automated DPI enhancement and TSV confidence thresholding. The system encapsulates extraction behind the `OcrEngine` interface.

## Consequences

### Positive
- Zero recurring API billing costs for document processing
- Strict data residency: document text remains exclusively inside the host infrastructure
- Clean pluggability via the `OcrEngine` strategy interface enables future engine additions
- Verified across automated unit readability tests and end-to-end upload scenarios

### Negative
- Deployment containers and developer machines require local Tesseract binary installation

### Neutral
- Apache Tika dependencies bundle PDFBox, providing out-of-the-box PDF rendering without auxiliary libraries
