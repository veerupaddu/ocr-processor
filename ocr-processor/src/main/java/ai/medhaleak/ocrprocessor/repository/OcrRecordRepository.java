package ai.medhaleak.ocrprocessor.repository;

import ai.medhaleak.ocrprocessor.model.OcrRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcrRecordRepository extends JpaRepository<OcrRecord, UUID> {

    Optional<OcrRecord> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            SELECT * FROM ocr_records
            WHERE user_id = :userId
              AND (:query = '' OR search_vector @@ plainto_tsquery('english', :query))
            ORDER BY
              CASE WHEN :query = '' THEN 0 ELSE -ts_rank(search_vector, plainto_tsquery('english', :query)) END,
              created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM ocr_records
            WHERE user_id = :userId
              AND (:query = '' OR search_vector @@ plainto_tsquery('english', :query))
            """,
            nativeQuery = true)
    Page<OcrRecord> searchByUser(@Param("userId") UUID userId,
                                  @Param("query") String query,
                                  Pageable pageable);
}
