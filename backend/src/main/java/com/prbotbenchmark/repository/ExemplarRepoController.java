package com.prbotbenchmark.repository;

import com.prbotbenchmark.github.dto.GitHubPr;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.repository.dto.RegisterRepoRequest;
import com.prbotbenchmark.repository.dto.UpdateRepoRequest;
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
@RequestMapping("/api/repos")
public class ExemplarRepoController {

    private final ExemplarRepoService exemplarRepoService;

    public ExemplarRepoController(ExemplarRepoService exemplarRepoService) {
        this.exemplarRepoService = exemplarRepoService;
    }

    @GetMapping
    public ResponseEntity<List<ExemplarRepo>> listAll() {
        return ResponseEntity.ok(exemplarRepoService.listAll());
    }

    @PostMapping
    public ResponseEntity<ExemplarRepo> register(@RequestBody RegisterRepoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exemplarRepoService.register(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExemplarRepo> getById(@PathVariable Long id) {
        return ResponseEntity.ok(exemplarRepoService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExemplarRepo> update(@PathVariable Long id, @RequestBody UpdateRepoRequest req) {
        return ResponseEntity.ok(exemplarRepoService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        exemplarRepoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/prs")
    public ResponseEntity<List<GitHubPr>> listExemplarPrs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "all") String state) {
        return ResponseEntity.ok(exemplarRepoService.listExemplarPrs(id, state));
    }
}
