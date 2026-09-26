package ai.medhaleak.ocrprocessor.unit.testing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These cases fail on purpose so the Tests tab can show where, why, and how to fix.
 */
class FailureSamplesTest {

    @Test
    void shortPassword_isAccepted() {
        String password = "abc";
        assertThat(password.length()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void docxUpload_isStored() {
        String type = "docx";
        assertThat(List.of("pdf", "png", "jpg", "jpeg", "tiff")).contains(type);
    }

    @Test
    void retryWhileComplete_updatesSummary() {
        String summaryStatus = "COMPLETE";
        assertThat(summaryStatus).isEqualTo("FAILED");
    }
}
