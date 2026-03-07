package com.prbotbenchmark.runner;

import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.ScheduledRun;
import com.prbotbenchmark.runner.dto.CreateScheduledRunRequest;
import com.prbotbenchmark.runner.dto.UpdateScheduledRunRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduled-runs")
@RequiredArgsConstructor
public class ScheduledRunController {

    private final ScheduledRunService scheduledRunService;

    @GetMapping
    public ResponseEntity<List<ScheduledRun>> listAll(
            @RequestParam(required = false) Long suiteId) {
        if (suiteId != null) {
            return ResponseEntity.ok(scheduledRunService.listBySuite(suiteId));
        }
        return ResponseEntity.ok(scheduledRunService.listAll());
    }

    @PostMapping
    public ResponseEntity<ScheduledRun> create(@RequestBody CreateScheduledRunRequest request) {
        ScheduledRun scheduledRun = scheduledRunService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduledRun);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduledRun> getById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduledRunService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduledRun> update(
            @PathVariable Long id,
            @RequestBody UpdateScheduledRunRequest request) {
        return ResponseEntity.ok(scheduledRunService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduledRunService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<BenchmarkRun> triggerNow(@PathVariable Long id) {
        BenchmarkRun benchmarkRun = scheduledRunService.triggerNow(id);
        return ResponseEntity.ok(benchmarkRun);
    }
}
