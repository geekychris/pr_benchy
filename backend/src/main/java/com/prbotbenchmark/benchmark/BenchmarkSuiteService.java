package com.prbotbenchmark.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prbotbenchmark.benchmark.domain.BenchmarkSuite;
import com.prbotbenchmark.benchmark.domain.SuitePr;
import com.prbotbenchmark.benchmark.dto.AddPrRequest;
import com.prbotbenchmark.benchmark.dto.CreateSuiteRequest;
import com.prbotbenchmark.benchmark.dto.UpdateSuiteRequest;
import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.dto.GitHubPr;
import com.prbotbenchmark.repository.ExemplarRepoRepository;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class BenchmarkSuiteService {

    private final BenchmarkSuiteRepository benchmarkSuiteRepository;
    private final SuitePrRepository suitePrRepository;
    private final ExemplarRepoRepository exemplarRepoRepository;
    private final GitHubApiClient gitHubApiClient;
    private final ObjectMapper objectMapper;

    public BenchmarkSuiteService(BenchmarkSuiteRepository benchmarkSuiteRepository,
                                 SuitePrRepository suitePrRepository,
                                 ExemplarRepoRepository exemplarRepoRepository,
                                 GitHubApiClient gitHubApiClient,
                                 ObjectMapper objectMapper) {
        this.benchmarkSuiteRepository = benchmarkSuiteRepository;
        this.suitePrRepository = suitePrRepository;
        this.exemplarRepoRepository = exemplarRepoRepository;
        this.gitHubApiClient = gitHubApiClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BenchmarkSuite> listAll() {
        return benchmarkSuiteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BenchmarkSuite> listByRepo(Long repoId) {
        return benchmarkSuiteRepository.findByExemplarRepoId(repoId);
    }

    @Transactional(readOnly = true)
    public BenchmarkSuite getById(Long id) {
        return benchmarkSuiteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "BenchmarkSuite not found with id: " + id));
    }

    public BenchmarkSuite create(CreateSuiteRequest req) {
        ExemplarRepo exemplarRepo = exemplarRepoRepository.findById(req.exemplarRepoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ExemplarRepo not found with id: " + req.exemplarRepoId()));

        BenchmarkSuite suite = new BenchmarkSuite();
        suite.setName(req.name());
        suite.setDescription(req.description());
        suite.setExemplarRepo(exemplarRepo);
        suite.setCreatedAt(Instant.now());
        suite.setUpdatedAt(Instant.now());

        return benchmarkSuiteRepository.save(suite);
    }

    public BenchmarkSuite update(Long id, UpdateSuiteRequest req) {
        BenchmarkSuite suite = getById(id);

        if (req.name() != null) {
            suite.setName(req.name());
        }
        if (req.description() != null) {
            suite.setDescription(req.description());
        }
        suite.setUpdatedAt(Instant.now());

        return benchmarkSuiteRepository.save(suite);
    }

    public void delete(Long id) {
        BenchmarkSuite suite = getById(id);
        benchmarkSuiteRepository.delete(suite);
    }

    @Transactional(readOnly = true)
    public List<SuitePr> listSuitePrs(Long suiteId) {
        return suitePrRepository.findBySuiteId(suiteId);
    }

    public SuitePr addPr(Long suiteId, AddPrRequest req) {
        BenchmarkSuite suite = getById(suiteId);
        ExemplarRepo repo = suite.getExemplarRepo();

        GitHubPr ghPr;
        try {
            ghPr = gitHubApiClient.getPullRequest(repo.getOwner(), repo.getRepoName(), req.originalPrNumber());
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch PR from GitHub: " + e.getMessage(), e);
        }

        List<String> labelNames = ghPr.labels() != null
                ? ghPr.labels().stream().map(l -> l.name()).collect(Collectors.toList())
                : List.of();

        String labelsJson;
        try {
            labelsJson = objectMapper.writeValueAsString(labelNames);
        } catch (JsonProcessingException e) {
            labelsJson = "[]";
        }

        SuitePr suitePr = new SuitePr();
        suitePr.setSuite(suite);
        suitePr.setOriginalPrNumber(ghPr.number());
        suitePr.setTitle(ghPr.title());
        suitePr.setDescription(ghPr.body());
        suitePr.setBaseCommitSha(ghPr.base().sha());
        suitePr.setHeadCommitSha(ghPr.head().sha());
        suitePr.setBaseBranch(ghPr.base().ref());
        suitePr.setHeadBranch(ghPr.head().ref());
        suitePr.setAuthor(ghPr.user().login());
        suitePr.setMergedAt(ghPr.mergedAt());
        suitePr.setFilesChanged(ghPr.changedFiles());
        suitePr.setAdditions(ghPr.additions());
        suitePr.setDeletions(ghPr.deletions());
        suitePr.setLabelsJson(labelsJson);
        suitePr.setCreatedAt(Instant.now());

        return suitePrRepository.save(suitePr);
    }

    public void removePr(Long suiteId, Long prId) {
        suitePrRepository.deleteBySuiteIdAndId(suiteId, prId);
    }
}
