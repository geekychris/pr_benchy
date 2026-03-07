package com.prbotbenchmark.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prbotbenchmark.benchmark.BenchmarkSuiteRepository;
import com.prbotbenchmark.bot.BotRepository;
import com.prbotbenchmark.runner.domain.BenchmarkRun;
import com.prbotbenchmark.runner.domain.ScheduledRun;
import com.prbotbenchmark.runner.dto.CreateRunRequest;
import com.prbotbenchmark.runner.dto.CreateScheduledRunRequest;
import com.prbotbenchmark.runner.dto.UpdateScheduledRunRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
public class ScheduledRunService {

    private final ScheduledRunRepository scheduledRunRepository;
    private final BenchmarkRunService benchmarkRunService;
    private final BotRepository botRepository;
    private final BenchmarkSuiteRepository benchmarkSuiteRepository;
    private final ObjectMapper objectMapper;

    public ScheduledRunService(ScheduledRunRepository scheduledRunRepository,
                               BenchmarkRunService benchmarkRunService,
                               BotRepository botRepository,
                               BenchmarkSuiteRepository benchmarkSuiteRepository,
                               ObjectMapper objectMapper) {
        this.scheduledRunRepository = scheduledRunRepository;
        this.benchmarkRunService = benchmarkRunService;
        this.botRepository = botRepository;
        this.benchmarkSuiteRepository = benchmarkSuiteRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ScheduledRun> listAll() {
        return scheduledRunRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ScheduledRun> listBySuite(Long suiteId) {
        return scheduledRunRepository.findBySuiteId(suiteId);
    }

    @Transactional(readOnly = true)
    public ScheduledRun getById(Long id) {
        return scheduledRunRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "ScheduledRun not found with id: " + id));
    }

    public ScheduledRun create(CreateScheduledRunRequest req) {
        // Validate cron expression
        try {
            CronExpression.parse(req.cronExpression());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid cron expression: " + req.cronExpression());
        }

        // Validate suite exists
        var suite = benchmarkSuiteRepository.findById(req.suiteId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "BenchmarkSuite not found with id: " + req.suiteId()));

        // Validate bots exist
        if (req.botIds() == null || req.botIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one bot must be specified");
        }
        List<?> bots = botRepository.findAllById(req.botIds());
        if (bots.size() != req.botIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more bot IDs not found");
        }

        // Serialize botIds to JSON string
        String botIdsJson;
        try {
            botIdsJson = objectMapper.writeValueAsString(req.botIds());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize botIds");
        }

        // Compute nextRunAt
        CronExpression cron = CronExpression.parse(req.cronExpression());
        ZonedDateTime next = cron.next(ZonedDateTime.now());
        Instant nextRunAt = next != null ? next.toInstant() : null;

        ScheduledRun scheduledRun = ScheduledRun.builder()
                .name(req.name())
                .suite(suite)
                .botIds(botIdsJson)
                .cronExpression(req.cronExpression())
                .concurrency(req.concurrency() != null ? req.concurrency() : 2)
                .goldenDatasetEnabled(req.goldenDatasetEnabled() != null ? req.goldenDatasetEnabled() : true)
                .enabled(true)
                .nextRunAt(nextRunAt)
                .build();

        return scheduledRunRepository.save(scheduledRun);
    }

    public ScheduledRun update(Long id, UpdateScheduledRunRequest req) {
        ScheduledRun scheduledRun = getById(id);

        if (req.name() != null) {
            scheduledRun.setName(req.name());
        }
        if (req.botIds() != null) {
            try {
                scheduledRun.setBotIds(objectMapper.writeValueAsString(req.botIds()));
            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize botIds");
            }
        }
        if (req.cronExpression() != null) {
            try {
                CronExpression.parse(req.cronExpression());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid cron expression: " + req.cronExpression());
            }
            scheduledRun.setCronExpression(req.cronExpression());
            // Recompute nextRunAt since cron changed
            CronExpression cron = CronExpression.parse(req.cronExpression());
            ZonedDateTime next = cron.next(ZonedDateTime.now());
            scheduledRun.setNextRunAt(next != null ? next.toInstant() : null);
        }
        if (req.concurrency() != null) {
            scheduledRun.setConcurrency(req.concurrency());
        }
        if (req.goldenDatasetEnabled() != null) {
            scheduledRun.setGoldenDatasetEnabled(req.goldenDatasetEnabled());
        }
        if (req.enabled() != null) {
            scheduledRun.setEnabled(req.enabled());
        }

        return scheduledRunRepository.save(scheduledRun);
    }

    public void delete(Long id) {
        ScheduledRun scheduledRun = getById(id);
        scheduledRunRepository.delete(scheduledRun);
    }

    public BenchmarkRun triggerNow(Long id) {
        ScheduledRun scheduledRun = getById(id);

        List<Long> botIds;
        try {
            botIds = objectMapper.readValue(scheduledRun.getBotIds(), new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize botIds");
        }

        CreateRunRequest runRequest = new CreateRunRequest(
                scheduledRun.getSuite().getId(),
                scheduledRun.getName(),
                botIds,
                scheduledRun.getConcurrency(),
                scheduledRun.getGoldenDatasetEnabled()
        );

        log.info("Triggering scheduled run id={} name='{}'", scheduledRun.getId(), scheduledRun.getName());
        BenchmarkRun benchmarkRun = benchmarkRunService.createAndStart(runRequest);

        scheduledRun.setLastRunAt(Instant.now());
        scheduledRun.setLastRunId(benchmarkRun.getId());
        scheduledRun.setLastRunStatus(benchmarkRun.getStatus().name());
        scheduledRunRepository.save(scheduledRun);

        return benchmarkRun;
    }

    public void checkAndTriggerDue() {
        List<ScheduledRun> enabledRuns = scheduledRunRepository.findByEnabledTrue();
        Instant now = Instant.now();

        for (ScheduledRun scheduledRun : enabledRuns) {
            if (scheduledRun.getNextRunAt() != null
                    && scheduledRun.getNextRunAt().isBefore(now)
                    && (scheduledRun.getLastRunAt() == null
                        || !"RUNNING".equals(scheduledRun.getLastRunStatus()))) {

                triggerNow(scheduledRun.getId());

                // Compute and update nextRunAt for the next occurrence
                try {
                    CronExpression cron = CronExpression.parse(scheduledRun.getCronExpression());
                    ZonedDateTime next = cron.next(ZonedDateTime.now());
                    scheduledRun.setNextRunAt(next != null ? next.toInstant() : null);
                    scheduledRunRepository.save(scheduledRun);
                } catch (IllegalArgumentException e) {
                    log.error("Failed to compute next run time for scheduled run id={}", scheduledRun.getId(), e);
                }
            }
        }
    }
}
