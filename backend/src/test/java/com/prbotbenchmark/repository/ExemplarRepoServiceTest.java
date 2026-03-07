package com.prbotbenchmark.repository;

import com.prbotbenchmark.github.GitHubApiClient;
import com.prbotbenchmark.github.GitHubAppProperties;
import com.prbotbenchmark.repository.domain.ExemplarRepo;
import com.prbotbenchmark.repository.dto.RegisterRepoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExemplarRepoServiceTest {

    @Mock
    private ExemplarRepoRepository repository;

    @Mock
    private GitHubApiClient apiClient;

    @Mock
    private GitHubAppProperties props;

    private ExemplarRepoService service;

    @BeforeEach
    void setUp() {
        service = new ExemplarRepoService(repository, apiClient, props);
    }

    @ParameterizedTest(name = "URL: {0} -> owner: {1}, repo: {2}")
    @CsvSource({
        "https://github.com/myorg/myrepo,          myorg, myrepo",
        "https://github.com/myorg/myrepo.git,      myorg, myrepo",
        "https://github.com/myorg/myrepo/,         myorg, myrepo",
        "https://github.com/geekychris/agentic_pr_review, geekychris, agentic_pr_review",
    })
    void register_parsesOwnerAndRepoFromUrl(String url, String expectedOwner, String expectedRepo) {
        when(props.defaultMirrorOrg()).thenReturn("myorg");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterRepoRequest req = new RegisterRepoRequest("My Repo", url.trim(), null, null);
        ExemplarRepo result = service.register(req);

        assertThat(result.getOwner()).isEqualTo(expectedOwner);
        assertThat(result.getRepoName()).isEqualTo(expectedRepo);
    }

    @Test
    void getById_notFound_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getById_found_returnsRepo() {
        ExemplarRepo repo = new ExemplarRepo();
        repo.setId(1L);
        repo.setName("test");
        when(repository.findById(1L)).thenReturn(Optional.of(repo));

        assertThat(service.getById(1L).getName()).isEqualTo("test");
    }

    @Test
    void delete_callsRepositoryDeleteById() {
        ExemplarRepo repo = new ExemplarRepo();
        repo.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(repo));

        service.delete(1L);

        verify(repository).delete(repo);
    }
}
