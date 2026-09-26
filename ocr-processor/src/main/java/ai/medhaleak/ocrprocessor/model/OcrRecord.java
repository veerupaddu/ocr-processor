package ai.medhaleak.ocrprocessor.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ocr_records")
public class OcrRecord {

    public enum Status { PENDING, PROCESSING, COMPLETE, FAILED }
    public enum SummaryStatus { PENDING, COMPLETE, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false, length = 20)
    private SummaryStatus summaryStatus = SummaryStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public OcrRecord() {}

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public SummaryStatus getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(SummaryStatus summaryStatus) { this.summaryStatus = summaryStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
