package com.prbotbenchmark.repository;

import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.GitHubAppProperties;
import com.prbotbenchmark.github.dto.GitHubPr;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.repository.dto.RegisterRepoRequest;
import com.prbotbenchmark.repository.dto.UpdateRepoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class ExemplarRepoService {

    private final ExemplarRepoRepository exemplarRepoRepository;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppProperties gitHubAppProperties;

    public ExemplarRepoService(ExemplarRepoRepository exemplarRepoRepository,
                               GitHubApiClient gitHubApiClient,
                               GitHubAppProperties gitHubAppProperties) {
        this.exemplarRepoRepository = exemplarRepoRepository;
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubAppProperties = gitHubAppProperties;
    }

    @Transactional(readOnly = true)
    public List<ExemplarRepo> listAll() {
        return exemplarRepoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ExemplarRepo getById(Long id) {
        return exemplarRepoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ExemplarRepo not found with id: " + id));
    }

    public ExemplarRepo register(RegisterRepoRequest req) {
        String githubUrl = req.githubUrl();
        // Parse owner and repoName from URL: "https://github.com/owner/repo[.git][/]"
        String normalizedUrl = githubUrl.replaceAll("/+$", "").replaceAll("\\.git$", "");
        String[] parts = normalizedUrl.split("/");
        String owner = parts[parts.length - 2];
        String repoName = parts[parts.length - 1];

        String mirrorOrg = req.mirrorOrg() != null ? req.mirrorOrg() : gitHubAppProperties.defaultMirrorOrg();

        ExemplarRepo repo = new ExemplarRepo();
        repo.setName(req.name());
        repo.setGithubUrl(githubUrl);
        repo.setOwner(owner);
        repo.setRepoName(repoName);
        repo.setMirrorOrg(mirrorOrg);
        repo.setMirrorRepoName(req.mirrorRepoName());
        repo.setSyncStatus("PENDING");
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());

        return exemplarRepoRepository.save(repo);
    }

    public ExemplarRepo update(Long id, UpdateRepoRequest req) {
        ExemplarRepo repo = getById(id);

        if (req.name() != null) {
            repo.setName(req.name());
        }
        if (req.mirrorOrg() != null) {
            repo.setMirrorOrg(req.mirrorOrg());
        }
        if (req.mirrorRepoName() != null) {
            repo.setMirrorRepoName(req.mirrorRepoName());
        }
        if (req.defaultBranch() != null) {
            repo.setDefaultBranch(req.defaultBranch());
        }
        repo.setUpdatedAt(Instant.now());

        return exemplarRepoRepository.save(repo);
    }

    public void delete(Long id) {
        ExemplarRepo repo = getById(id);
        exemplarRepoRepository.delete(repo);
    }

    @Transactional(readOnly = true)
    public List<GitHubPr> listExemplarPrs(Long id, String state) {
        ExemplarRepo repo = getById(id);
        try {
            return gitHubApiClient.listPullRequests(repo.getOwner(), repo.getRepoName(), state);
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch PRs from GitHub: " + e.getMessage(), e);
        }
    }

    public void markSyncStarted(Long id) {
        ExemplarRepo repo = getById(id);
        repo.setSyncStatus("SYNCING");
        repo.setLastSyncedAt(Instant.now());
        exemplarRepoRepository.save(repo);
    }

    public void markSyncCompleted(Long id) {
        ExemplarRepo repo = getById(id);
        repo.setSyncStatus("SYNCED");
        repo.setLastSyncedAt(Instant.now());
        exemplarRepoRepository.save(repo);
    }

    public void markSyncFailed(Long id, String error) {
        ExemplarRepo repo = getById(id);
        repo.setSyncStatus("FAILED");
        exemplarRepoRepository.save(repo);
    }
}
