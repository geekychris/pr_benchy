package com.prbotbenchmark.grading;

import com.prbotbenchmark.grading.domain.IssueType;
import com.prbotbenchmark.grading.dto.CreateIssueTypeRequest;
import com.prbotbenchmark.grading.dto.UpdateIssueTypeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/issue-types")
@RequiredArgsConstructor
public class IssueTypeController {

    private final IssueTypeService issueTypeService;

    @GetMapping
    public ResponseEntity<List<IssueType>> listAll(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(issueTypeService.listAll(activeOnly));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> listCategories() {
        return ResponseEntity.ok(issueTypeService.listCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IssueType> getById(@PathVariable Long id) {
        return ResponseEntity.ok(issueTypeService.getById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<IssueType> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(issueTypeService.getByCode(code));
    }

    @PostMapping
    public ResponseEntity<IssueType> create(@RequestBody CreateIssueTypeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(issueTypeService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IssueType> update(@PathVariable Long id,
                                             @RequestBody UpdateIssueTypeRequest req) {
        return ResponseEntity.ok(issueTypeService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        issueTypeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
