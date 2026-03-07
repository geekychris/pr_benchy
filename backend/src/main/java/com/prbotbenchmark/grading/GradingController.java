package com.prbotbenchmark.grading;

import com.prbotbenchmark.grading.domain.GoldenDatasetEntry;
import com.prbotbenchmark.grading.domain.Grading;
import com.prbotbenchmark.grading.dto.BulkGradeRequest;
import com.prbotbenchmark.grading.dto.BulkGradeResult;
import com.prbotbenchmark.grading.dto.CreateGradingRequest;
import com.prbotbenchmark.grading.dto.GradingProgressDto;
import com.prbotbenchmark.grading.dto.PromoteToGoldenRequest;
import com.prbotbenchmark.grading.dto.UngradedCommentDto;
import com.prbotbenchmark.grading.dto.UpdateGoldenEntryRequest;
import com.prbotbenchmark.grading.dto.UpdateGradingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class GradingController {

    private final GradingService gradingService;

    // --- Grading endpoints ---

    @PostMapping("/api/gradings")
    public ResponseEntity<Grading> createGrading(@RequestBody CreateGradingRequest req) {
        Grading grading = gradingService.createGrading(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(grading);
    }

    @PutMapping("/api/gradings/{id}")
    public ResponseEntity<Grading> updateGrading(@PathVariable Long id,
                                                  @RequestBody UpdateGradingRequest req) {
        Grading grading = gradingService.updateGrading(id, req);
        return ResponseEntity.ok(grading);
    }

    @DeleteMapping("/api/gradings/{id}")
    public ResponseEntity<Void> deleteGrading(@PathVariable Long id) {
        gradingService.deleteGrading(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/comments/{commentId}/gradings")
    public ResponseEntity<List<Grading>> getGradingsForComment(@PathVariable Long commentId,
                                                                @RequestParam(defaultValue = "REVIEW") String type) {
        List<Grading> gradings = gradingService.getGradingsForComment(commentId, type);
        return ResponseEntity.ok(gradings);
    }

    // --- Golden dataset endpoints ---

    @GetMapping("/api/golden-dataset")
    public ResponseEntity<List<GoldenDatasetEntry>> listGoldenDataset(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<GoldenDatasetEntry> entries = gradingService.listGoldenDataset(activeOnly);
        return ResponseEntity.ok(entries);
    }

    @PostMapping("/api/golden-dataset/promote")
    public ResponseEntity<GoldenDatasetEntry> promoteToGoldenDataset(
            @RequestBody PromoteToGoldenRequest req) {
        GoldenDatasetEntry entry = gradingService.promoteToGoldenDataset(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @PutMapping("/api/golden-dataset/{id}")
    public ResponseEntity<GoldenDatasetEntry> updateGoldenEntry(@PathVariable Long id,
                                                                  @RequestBody UpdateGoldenEntryRequest req) {
        GoldenDatasetEntry entry = gradingService.updateGoldenEntry(id, req);
        return ResponseEntity.ok(entry);
    }

    @DeleteMapping("/api/golden-dataset/{id}")
    public ResponseEntity<Void> deactivateGoldenEntry(@PathVariable Long id) {
        gradingService.deactivateGoldenEntry(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/golden-dataset/export")
    public ResponseEntity<String> exportToJson() {
        String json = gradingService.exportToJson();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"golden-dataset.json\"");
        return ResponseEntity.ok().headers(headers).body(json);
    }

    @PostMapping("/api/golden-dataset/import")
    public ResponseEntity<Void> importFromJson(@RequestBody String json) {
        gradingService.importFromJson(json);
        return ResponseEntity.ok().build();
    }

    // --- Grading queue / bulk grading endpoints ---

    @GetMapping("/api/grading-queue")
    public ResponseEntity<?> getGradingQueue(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(defaultValue = "50") int limit) {
        if (runId == null && suiteId == null) {
            return ResponseEntity.badRequest().body("At least one of runId or suiteId must be provided");
        }
        List<UngradedCommentDto> queue = gradingService.getGradingQueue(runId, suiteId, limit);
        return ResponseEntity.ok(queue);
    }

    @PostMapping("/api/gradings/bulk")
    public ResponseEntity<BulkGradeResult> bulkGrade(@RequestBody BulkGradeRequest req) {
        BulkGradeResult result = gradingService.bulkGrade(req);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/grading-progress")
    public ResponseEntity<?> getGradingProgress(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) Long suiteId) {
        if (runId == null && suiteId == null) {
            return ResponseEntity.badRequest().body("At least one of runId or suiteId must be provided");
        }
        GradingProgressDto progress = gradingService.getProgress(runId, suiteId);
        return ResponseEntity.ok(progress);
    }
}
