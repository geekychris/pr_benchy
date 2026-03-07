package com.prbotbenchmark.runner;

import com.prbotbenchmark.benchmark.BenchmarkSuiteRepository;
import com.prbotbenchmark.benchmark.SuitePrRepository;
import com.prbotbenchmark.benchmark.domain.BenchmarkSuite;
import com.prbotbenchmark.bot.BotRepository;
import com.prbotbenchmark.bot.domain.Bot;
import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.BotSnapshot;
import com.prbotbenchmark.runner.domain.ReplayPr;
import com.prbotbenchmark.runner.domain.ReplayStatus;
import com.prbotbenchmark.runner.domain.RunStatus;
import com.prbotbenchmark.runner.dto.CreateRunRequest;
import com.prbotbenchmark.runner.dto.RunProgressDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BenchmarkRunService {

    private final BenchmarkRunRepository benchmarkRunRepository;
    private final ReplayPrRepository replayPrRepository;
    private final BenchmarkSuiteRepository benchmarkSuiteRepository;
    private final BotRepository botRepository;
    private final SuitePrRepository suitePrRepository;
    private final RunOrchestrator runOrchestrator;
    private final BotSnapshotRepository botSnapshotRepository;

    @Transactional(readOnly = true)
    public List<BenchmarkRun> listAll() {
        return benchmarkRunRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BenchmarkRun> listBySuite(Long suiteId) {
        return benchmarkRunRepository.findBySuiteId(suiteId);
    }

    @Transactional(readOnly = true)
    public BenchmarkRun getById(Long id) {
        return benchmarkRunRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "BenchmarkRun not found with id: " + id));
    }

    public BenchmarkRun createAndStart(CreateRunRequest req) {
        BenchmarkSuite suite = benchmarkSuiteRepository.findById(req.suiteId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "BenchmarkSuite not found with id: " + req.suiteId()));

        if (req.botIds() == null || req.botIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one bot must be specified");
        }

        List<Bot> bots = botRepository.findAllById(req.botIds());
        if (bots.size() != req.botIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more bot IDs not found");
        }

        BenchmarkRun run = new BenchmarkRun();
        run.setSuite(suite);
        run.setName(req.name());
        run.setStatus(RunStatus.PENDING);
        run.setConcurrency(req.concurrency() != null ? req.concurrency() : 1);
        run.setGoldenDatasetEnabled(req.goldenDatasetEnabled() != null ? req.goldenDatasetEnabled() : false);
        run.setBots(bots);
        run.setCreatedAt(Instant.now());

        BenchmarkRun saved = benchmarkRunRepository.save(run);
        log.info("Created BenchmarkRun id={} name='{}' for suite id={}", saved.getId(), saved.getName(), suite.getId());

        for (Bot bot : bots) {
            BotSnapshot snapshot = BotSnapshot.builder()
                    .run(saved)
                    .bot(bot)
                    .name(bot.getName())
                    .workflowFileName(bot.getWorkflowFileName())
                    .workflowContent(bot.getWorkflowContent())
                    .waitStrategy(bot.getWaitStrategy())
                    .timeoutSeconds(bot.getTimeoutSeconds())
                    .build();
            botSnapshotRepository.save(snapshot);
        }
        log.info("Snapshotted {} bot(s) for BenchmarkRun id={}", bots.size(), saved.getId());

        runOrchestrator.startRunAsync(saved);

        return saved;
    }

    public void cancel(Long id) {
        BenchmarkRun run = getById(id);
        run.setStatus(RunStatus.CANCELLED);
        benchmarkRunRepository.save(run);
        log.info("Cancelled BenchmarkRun id={}", id);
    }

    @Transactional(readOnly = true)
    public List<ReplayPr> listReplayPrs(Long runId) {
        // Verify run exists
        getById(runId);
        return replayPrRepository.findByRunId(runId);
    }

    @Transactional(readOnly = true)
    public RunProgressDto getProgress(Long runId) {
        BenchmarkRun run = getById(runId);

        long total = replayPrRepository.countByRunId(runId);
        long pending = replayPrRepository.countByRunIdAndStatus(runId, ReplayStatus.PENDING);
        long creating = replayPrRepository.countByRunIdAndStatus(runId, ReplayStatus.CREATING_BRANCHES);
        long waiting = replayPrRepository.countByRunIdAndStatus(runId, ReplayStatus.WAITING_FOR_BOTS);
        long collecting = replayPrRepository.countByRunIdAndStatus(runId, ReplayStatus.COLLECTING_COMMENTS);
        long completed = replayPrRepository.countByRunIdAndStatus(runId, ReplayStatus.COMPLETED);
        long failed = replayPrRepository.countByRunIdAndStatus(runId, ReplayStatus.FAILED);

        return new RunProgressDto(
                runId,
                run.getStatus().name(),
                (int) total,
                (int) pending,
                (int) creating,
                (int) waiting,
                (int) collecting,
                (int) completed,
                (int) failed
        );
    }

    public void cleanupRun(Long runId) {
        // Verify run exists
        getById(runId);
        runOrchestrator.cleanupRun(runId);
    }

    @Transactional(readOnly = true)
    public List<BotSnapshot> getBotSnapshots(Long runId) {
        return botSnapshotRepository.findByRunId(runId);
    }
}
