package ai.medhaleak.ocrprocessor.controller;

import ai.medhaleak.ocrprocessor.testing.TestConsoleService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tests")
public class TestConsoleController {

    private final TestConsoleService testConsoleService;

    public TestConsoleController(TestConsoleService testConsoleService) {
        this.testConsoleService = testConsoleService;
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        return testConsoleService.snapshot();
    }

    @GetMapping("/screenshots/{caseId}")
    public ResponseEntity<byte[]> screenshot(@PathVariable String caseId) {
        byte[] png = testConsoleService.readScreenshot(caseId);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @PostMapping("/{suiteId}/run")
    public ResponseEntity<?> run(@PathVariable String suiteId, @RequestBody(required = false) RunRequest request) {
        try {
            List<String> cases = request == null ? null : request.cases();
            testConsoleService.start(suiteId, cases);
            return ResponseEntity.accepted().body(testConsoleService.snapshot());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("observation", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("observation", ex.getMessage()));
        }
    }

    public record RunRequest(List<String> cases) {
    }
}
