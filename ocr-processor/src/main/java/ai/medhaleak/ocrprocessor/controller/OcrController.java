package ai.medhaleak.ocrprocessor.controller;

import ai.medhaleak.ocrprocessor.dto.OcrRecordDto;
import ai.medhaleak.ocrprocessor.dto.OcrRecordSummaryDto;
import ai.medhaleak.ocrprocessor.service.OcrService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
@RequestMapping("/api/v1/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<OcrRecordDto> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentName", required = false) String documentName) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ocrService.processUpload(userId, file, documentName));
    }

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<Page<OcrRecordSummaryDto>> search(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ocrService.search(userId, query,
                PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<OcrRecordDto> getById(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(ocrService.getById(userId, id));
    }

    @PostMapping("/{id}/retry-summary")
    @ResponseBody
    public ResponseEntity<OcrRecordDto> retrySummary(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(ocrService.retrySummary(userId, id));
    }

    @GetMapping("/detail/{id}")
    public String detail(@AuthenticationPrincipal UUID userId,
                         @PathVariable UUID id, Model model) {
        model.addAttribute("record", ocrService.getById(userId, id));
        return "ocr/detail";
    }
}
