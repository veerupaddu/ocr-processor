package ai.medhaleak.ocrprocessor.testing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceCardTest {

    @Test
    void cardSplitsExecutedValidatedAndObserved() {
        EvidenceCard.EvidenceParts parts = EvidenceCard.EvidenceParts.parse(
                "Executed: uploaded test.png. Validated: HTTP 201. Observed: id=abc.");
        assertEquals("uploaded test.png", parts.executed());
        assertEquals("HTTP 201", parts.validated());
        assertEquals("id=abc", parts.observed());
    }

    @Test
    void cardIsWrittenOnceAndKept() throws Exception {
        Path dir = Files.createTempDirectory("evidence-card");
        Path file = dir.resolve("JwtUtilTest.round.png");
        EvidenceCard.writeIfAbsent(file, "JWT round trip", "PASSED", "2026-09-26 21:34:01", "2026-09-26 21:34:02",
                "Executed: issued a token. Validated: it parsed.");
        assertTrue(Files.size(file) > 8);
        byte[] first = Files.readAllBytes(file);
        EvidenceCard.writeIfAbsent(file, "other", "FAILED", "", "", "replaced");
        assertEquals(first.length, Files.size(file));
    }
}