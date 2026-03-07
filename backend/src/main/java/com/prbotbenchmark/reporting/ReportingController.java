package com.prbotbenchmark.reporting;

import com.prbotbenchmark.reporting.dto.BotTrendDto;
import com.prbotbenchmark.reporting.dto.ComparisonReportDto;
import com.prbotbenchmark.reporting.dto.RunReportDto;
import com.prbotbenchmark.reporting.dto.SignificanceTestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Slf4j
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunReportDto> generateRunReport(@PathVariable Long runId) {
        RunReportDto report = reportingService.generateRunReport(runId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/runs/{runId}/comparison")
    public ResponseEntity<ComparisonReportDto> compareWithGoldenDataset(@PathVariable Long runId) {
        ComparisonReportDto report = reportingService.compareWithGoldenDataset(runId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/bots/{botId}/trend")
    public ResponseEntity<BotTrendDto> getBotTrend(
            @PathVariable Long botId,
            @RequestParam Long suiteId,
            @RequestParam(defaultValue = "10") int limit) {
        BotTrendDto trend = reportingService.getBotTrend(botId, suiteId, limit);
        return ResponseEntity.ok(trend);
    }

    @GetMapping("/runs/{runAId}/significance")
    public ResponseEntity<SignificanceTestDto> testSignificance(
            @PathVariable Long runAId,
            @RequestParam Long compareRunId) {
        SignificanceTestDto result = reportingService.testSignificance(runAId, compareRunId);
        return ResponseEntity.ok(result);
    }
}
