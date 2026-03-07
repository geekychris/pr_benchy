package com.prbotbenchmark.reporting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exports")
@Slf4j
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/runs/{runId}/csv")
    public ResponseEntity<String> exportRunAsCsv(@PathVariable Long runId) {
        String csv = exportService.exportRunAsCsv(runId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"run-" + runId + "-comments.csv\"")
                .body(csv);
    }

    @GetMapping("/runs/{runId}/sarif")
    public ResponseEntity<String> exportRunAsSarif(@PathVariable Long runId) {
        String sarif = exportService.exportRunAsSarif(runId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/sarif+json"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"run-" + runId + "-findings.sarif\"")
                .body(sarif);
    }

    @GetMapping("/golden-dataset/csv")
    public ResponseEntity<String> exportGoldenDatasetAsCsv() {
        String csv = exportService.exportGoldenDatasetAsCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"golden-dataset.csv\"")
                .body(csv);
    }
}
