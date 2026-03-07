package com.prbotbenchmark.benchmark;

import com.prbotbenchmark.benchmark.domain.BenchmarkSuite;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.benchmark.dto.AddPrRequest;
import com.prbotbenchmark.benchmark.dto.CreateSuiteRequest;
import com.prbotbenchmark.benchmark.dto.UpdateSuiteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/suites")
public class BenchmarkSuiteController {

    private final BenchmarkSuiteService benchmarkSuiteService;

    public BenchmarkSuiteController(BenchmarkSuiteService benchmarkSuiteService) {
        this.benchmarkSuiteService = benchmarkSuiteService;
    }

    @GetMapping
    public ResponseEntity<List<BenchmarkSuite>> listAll(
            @RequestParam(required = false) Long repoId) {
        if (repoId != null) {
            return ResponseEntity.ok(benchmarkSuiteService.listByRepo(repoId));
        }
        return ResponseEntity.ok(benchmarkSuiteService.listAll());
    }

    @PostMapping
    public ResponseEntity<BenchmarkSuite> create(@RequestBody CreateSuiteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(benchmarkSuiteService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BenchmarkSuite> getById(@PathVariable Long id) {
        return ResponseEntity.ok(benchmarkSuiteService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BenchmarkSuite> update(@PathVariable Long id, @RequestBody UpdateSuiteRequest req) {
        return ResponseEntity.ok(benchmarkSuiteService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        benchmarkSuiteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/prs")
    public ResponseEntity<List<SuitePr>> listSuitePrs(@PathVariable Long id) {
        return ResponseEntity.ok(benchmarkSuiteService.listSuitePrs(id));
    }

    @PostMapping("/{id}/prs")
    public ResponseEntity<SuitePr> addPr(@PathVariable Long id, @RequestBody AddPrRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(benchmarkSuiteService.addPr(id, req));
    }

    @DeleteMapping("/{id}/prs/{prId}")
    public ResponseEntity<Void> removePr(@PathVariable Long id, @PathVariable Long prId) {
        benchmarkSuiteService.removePr(id, prId);
        return ResponseEntity.noContent().build();
    }
}
