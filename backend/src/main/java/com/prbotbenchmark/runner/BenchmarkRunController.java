package com.prbotbenchmark.runner;

import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.BotSnapshot;
import com.prbotbenchmark.runner.domain.ReplayPr;
import com.prbotbenchmark.runner.dto.CreateRunRequest;
import com.prbotbenchmark.runner.dto.RunProgressDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class BenchmarkRunController {

    private final BenchmarkRunService benchmarkRunService;

    @GetMapping
    public ResponseEntity<List<BenchmarkRun>> listRuns(
            @RequestParam(required = false) Long suiteId) {
        if (suiteId != null) {
            return ResponseEntity.ok(benchmarkRunService.listBySuite(suiteId));
        }
        return ResponseEntity.ok(benchmarkRunService.listAll());
    }

    @PostMapping
    public ResponseEntity<BenchmarkRun> createAndStart(@RequestBody CreateRunRequest request) {
        BenchmarkRun run = benchmarkRunService.createAndStart(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(run);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BenchmarkRun> getById(@PathVariable Long id) {
        return ResponseEntity.ok(benchmarkRunService.getById(id));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<RunProgressDto> getProgress(@PathVariable Long id) {
        return ResponseEntity.ok(benchmarkRunService.getProgress(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        benchmarkRunService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/replay-prs")
    public ResponseEntity<List<ReplayPr>> listReplayPrs(@PathVariable Long id) {
        return ResponseEntity.ok(benchmarkRunService.listReplayPrs(id));
    }

    @GetMapping("/{id}/bot-snapshots")
    public ResponseEntity<List<BotSnapshot>> getBotSnapshots(@PathVariable Long id) {
        return ResponseEntity.ok(benchmarkRunService.getBotSnapshots(id));
    }

    @PostMapping("/{id}/cleanup")
    public ResponseEntity<Void> cleanupRun(@PathVariable Long id) {
        benchmarkRunService.cleanupRun(id);
        return ResponseEntity.noContent().build();
    }
}
