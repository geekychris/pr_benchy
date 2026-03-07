# PR Bot Benchmark — Test Documentation

## Table of Contents

1. [Test Strategy and Philosophy](#1-test-strategy-and-philosophy)
2. [Running Tests](#2-running-tests)
3. [Test Categories](#3-test-categories)
4. [Git Integration Test In Depth](#4-git-integration-test-in-depth)
5. [Coverage Gaps and Roadmap](#5-coverage-gaps-and-roadmap)
6. [Adding Testcontainers for DB Tests](#6-adding-testcontainers-for-db-tests)
7. [Adding WireMock for GitHub API Tests](#7-adding-wiremock-for-github-api-tests)
8. [Testing Guidelines and Conventions](#8-testing-guidelines-and-conventions)
9. [CI/CD Integration](#9-cicd-integration)

---

## 1. Test Strategy and Philosophy

The test suite is organized into two distinct tiers that reflect the cost and scope of each test:

**Unit tests** cover pure logic with no external dependencies. They do not start a Spring context, open a database connection, or make network calls. This makes them fast (sub-second per class) and reliable. Mockito is used where a unit under test depends on a collaborator that would otherwise require I/O (e.g., a GitHub API client).

**Integration tests** verify behavior that crosses a process or I/O boundary. The current integration test validates real `git` operations by spawning actual `git` processes against temporary bare repositories on disk. This is intentional: mocking `ProcessBuilder` or wrapping `git` in an abstraction layer would hide the real failure modes (bad SHA formats, missing refs, permission errors, non-zero exit codes) that matter in production.

Key decisions:
- No in-memory git (JGit) in integration tests. The system uses `ProcessBuilder` + `git` CLI, so tests do the same.
- No `@SpringBootTest` for unit tests — the Spring context adds seconds of startup per test class.
- `assertj` fluent assertions throughout for readable failure messages.
- Parameterized tests (`@CsvSource`, `@MethodSource`) wherever a single logical case has multiple input variants.

---

## 2. Running Tests

All commands are run from the `backend/` directory.

```bash
# Run the full test suite
./gradlew test

# Run a single test class
./gradlew test --tests "*.TextNormalizerTest"

# Run the git integration test only
./gradlew test --tests "*.GitWorkspaceServiceIntegrationTest"

# Verbose output (prints each test name and stack traces inline)
./gradlew test --info

# Re-run even if nothing changed (Gradle caches test results)
./gradlew test --rerun-tasks
```

**HTML report** — after any test run, open:

```
backend/build/reports/tests/test/index.html
```

This report shows pass/fail per class and method, stdout/stderr captured during each test, and full stack traces for failures. It is the fastest way to debug a test that passes locally but fails in CI.

**XML results** for CI systems are written to:

```
backend/build/test-results/test/
```

---

## 3. Test Categories

All test classes live under `backend/src/test/java/com/prbotbenchmark/`.

### 3.1 Text Normalization — `collection/TextNormalizerTest`

Tests the `TextNormalizer` utility used to pre-process PR review comment text before fingerprinting and similarity scoring.

| What is tested | Why it matters |
|---|---|
| Markdown stripping (bold, italic, code fences, headers) | Raw markdown syntax should not affect similarity scores |
| URL removal | Two comments differing only by a link URL should score as near-identical |
| Case folding to lowercase | "LGTM" and "lgtm" are the same signal |
| Whitespace normalization (tabs, multiple spaces, newlines) | Prevents whitespace-only differences from inflating edit distance |
| Parameterized examples covering edge cases | Confirms behavior on empty strings, strings that are only markup, etc. |

### 3.2 Winnowing Hash Strategy — `similarity/WinnowingHashStrategyTest`

Tests the Winnowing fingerprinting algorithm, which selects a minimum set of rolling hashes from a text window to represent a document compactly.

| What is tested | Why it matters |
|---|---|
| Fingerprint determinism | Same input must always produce the same fingerprint set |
| Empty and null inputs | Guard against NPE in the pipeline |
| Jaccard similarity on known inputs | Validates the `[shared hashes] / [union of hashes]` formula |
| Near-duplicate detection | Two comments with minor edits should exceed the similarity threshold |

### 3.3 Exact Match Strategy — `similarity/ExactMatchStrategyTest`

Tests the simplest similarity strategy: byte-for-byte string equality.

| What is tested |
|---|
| Identical strings score 1.0 |
| Different strings score 0.0 |
| Null inputs are handled without throwing |
| Case sensitivity (exact match is case-sensitive by design) |

### 3.4 File/Line Strategy — `similarity/FileLineStrategyTest`

Tests the strategy that compares comments by their position in a diff (file path + line number), used to determine whether two comments are addressing the same location.

| What is tested |
|---|
| Same file and same line number → match |
| Same file, different line → no match |
| Different file → no match |
| Null line numbers are handled (inline comments vs. file-level comments) |

### 3.5 Normalized Text Strategy — `similarity/NormalizedTextStrategyTest`

Tests the Jaro-Winkler string distance strategy, which gives partial credit for similar but not identical text.

| What is tested | Why it matters |
|---|---|
| Identical texts score 1.0 | Sanity check for the upper bound |
| Very different texts score below threshold | Prevents false positives |
| Symmetry: `score(a,b) == score(b,a)` | Required property of any similarity metric |
| Threshold boundary cases | Confirms the accept/reject cutoff is applied correctly |
| Null inputs | Defensive check |

### 3.6 Exemplar Repo Service — `repository/ExemplarRepoServiceTest`

Tests the service layer for managing exemplar repositories (the GitHub repos whose PRs are replayed). Uses Mockito to mock the JPA repository and GitHub API client.

| What is tested |
|---|
| GitHub URL parsing (parameterized): extracts owner and repo name from https and git URLs |
| `getById` throws a typed `NOT_FOUND` exception when the ID does not exist |
| `delete` delegates to the repository and returns successfully |

### 3.7 Reporting Service — `reporting/ReportingServiceTest`

Tests the precision/recall/F1 arithmetic that produces the final benchmark score.

| What is tested | Why it matters |
|---|---|
| Standard precision, recall, F1 with known inputs | Regression guard on the core metric |
| Divide-by-zero when there are zero predicted or zero actual comments | Production data will have edge cases like empty bot runs |

---

## 4. Git Integration Test In Depth

**File:** `replay/GitWorkspaceServiceIntegrationTest.java`

### Why local bare repositories instead of mocking

`GitWorkspaceService` drives the replay pipeline by:
1. Creating a branch at a specific commit SHA in a remote repository.
2. Injecting a GitHub Actions workflow file and committing it.
3. Pushing a SHA to a named ref (`git push {sha}:refs/heads/{branch}`).
4. Cleaning up branches after a run completes.

These operations are thin wrappers around `ProcessBuilder` invoking real `git` commands. Mocking at the `ProcessBuilder` level would:
- Only verify that the right string arguments are assembled — not that git accepts them.
- Miss failures like "the SHA doesn't exist in the repo", "the remote ref is locked", or "the push target is a non-fast-forward".

Instead, each test method uses `@TempDir` to create a real bare git repository on the local filesystem and points `GitWorkspaceService` at it via a `file://` URL. This costs milliseconds per test and requires only that `git` is installed (always true in CI and dev environments).

### Test cases and what each validates

**`createBranchAtSha_validSha_branchExists`**
- Creates an initial commit in the bare repo to obtain a real SHA.
- Calls the service to create a branch pointing at that SHA.
- Verifies with `git rev-parse refs/heads/{branch}` that the branch was created and points to the correct commit.

**`injectWorkflowAndCommit_createsNewCommit`**
- After branch creation, calls the workflow injection method.
- Verifies that a new commit was added on top of the branch (commit count increases by one).
- Verifies the workflow file appears in `git ls-tree` output at the expected path.

**`pushShaToRef_coreReplayOperation`**
- Exercises `git push {sha}:refs/heads/{branch}`, which is the exact command used to set up the PR head during replay.
- Verifies the ref on the "remote" (bare repo) resolves to the pushed SHA.
- This is the highest-value test in the suite because this push pattern is unusual and easy to get wrong with quoting or ref formatting.

**`createBaseBranchAndHeadBranch_atDifferentShas`**
- Creates two commits in sequence, then calls the service to set up base and head branches at those two different SHAs.
- Mirrors the actual replay setup where base and head must diverge by exactly the PR's diff.

**`deleteBranch_branchIsRemoved`**
- Calls the cleanup method and verifies that `git rev-parse refs/heads/{branch}` exits non-zero afterward.
- Ensures cleanup does not leave orphaned refs that would confuse subsequent runs.

**`invalidGitCommand_throwsRuntimeException`**
- Passes a deliberately malformed argument to a git subcommand.
- Verifies that the service wraps the non-zero exit code in a `RuntimeException` rather than silently succeeding.
- This confirms the error-handling path is exercised.

### What `@TempDir` provides

JUnit 5 injects a fresh `java.nio.file.Path` for each test method. The directory is automatically deleted after the test completes, even on failure. This means:
- No test leaves artifacts on disk.
- Tests can run in parallel without repo collisions.
- No manual `@AfterEach` cleanup is needed.

---

## 5. Coverage Gaps and Roadmap

The following areas have no automated test coverage today. They are ordered roughly by risk and implementation effort.

### 5.1 Repository Layer (`@DataJpaTest`)

These tests use Spring's `@DataJpaTest` slice, which starts only the JPA context and an embedded H2 database. No web layer, no service beans.

| Repository | What to test |
|---|---|
| `ExemplarRepoRepository` | `findByOwnerAndRepoName` returns correct entity; inserting a duplicate owner+repo name violates the unique constraint |
| `SuitePrRepository` | Unique constraint on `(suite_id, original_pr_number)` is enforced at the DB level |
| `BenchmarkRunRepository` | `findByStatus` returns only runs in the given status enum value |
| `ReviewCommentRepository` | `findByReplayPrRunId` executes the cross-join correctly and does not return comments from other runs |

### 5.2 API Layer (`@SpringBootTest` + MockMvc + Testcontainers)

These tests boot the full Spring application against a real PostgreSQL instance (see Section 6).

| Controller | Key scenarios |
|---|---|
| `ExemplarRepoControllerTest` | `POST /repos` creates entity and returns 201; `GET /repos/{id}` returns 404 for unknown ID; `DELETE /repos/{id}` removes and returns 204 |
| `BotControllerTest` | Full CRUD; verify 409 Conflict on duplicate bot name |
| `BenchmarkSuiteControllerTest` | Create suite; add PR to suite; remove PR from suite; verify PR list reflects changes |
| `SetupControllerTest` | `GET /setup/status` returns `configured: false` when the GitHub App private key env var is absent |

### 5.3 GitHub API Client (WireMock)

See Section 7 for setup. These tests verify HTTP-level behavior without hitting GitHub.

| Class | What to test |
|---|---|
| `GitHubApiClient` | `POST /app/installations/{id}/access_tokens` returns a token; 401 response triggers a retry with a refreshed JWT; rate-limit response (429) is surfaced as a typed exception |
| `GitHubAppAuthService` | JWT is generated with the correct `iss` claim and expiry; token is cached and reused within its validity window; cached token is discarded and refreshed after expiry |

For `GitHubAppAuthService` JWT tests, generate a throwaway RSA key pair in `@BeforeAll` using `KeyPairGenerator` — do not commit a real private key to the repository.

### 5.4 RunOrchestrator

| Scenario | How to test |
|---|---|
| Semaphore limits concurrent replay PRs | Use `CountDownLatch` to hold N tasks inside the service, submit N+1, assert the N+1th is queued rather than running |
| Cancellation stops new replay PRs | Mark a run as cancelled via the repository mock, verify no further PRs are submitted to the GitHub API after the cancellation is observed |
| Bot timeout is non-fatal | Configure a very short timeout in the test, have the mock bot not respond, verify the run completes with a warning status rather than an exception |

### 5.5 Frontend (Future)

Use Vitest + React Testing Library. Priority areas:

- Grading form submission: mock `fetch`, submit the form, verify the request payload and that success state is shown.
- PR picker filtering: render the component with a fixture list of PRs, type into the search box, verify only matching PRs remain visible.
- Progress auto-refresh: mock timers with `vi.useFakeTimers()`, advance time, verify that the polling callback fires and updates the displayed status.

---

## 6. Adding Testcontainers for DB Tests

Testcontainers spins up a real PostgreSQL container for the duration of the test run, then tears it down. This eliminates the H2/PostgreSQL dialect divergence that hides bugs in native queries and JSON column mappings.

### Gradle dependencies

Add to `backend/build.gradle`:

```groovy
dependencies {
    testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
    testImplementation 'org.testcontainers:postgresql:1.19.7'
}
```

### Example: `ExemplarRepoControllerTest`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ExemplarRepoControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("benchmark_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void createRepo_validPayload_returns201() throws Exception {
        mockMvc.perform(post("/api/repos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"githubUrl": "https://github.com/owner/repo"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner").value("owner"))
                .andExpect(jsonPath("$.repoName").value("repo"));
    }
}
```

**Performance note:** declare the container as `static` so it is shared across all test methods in the class (started once per class, not once per test). For further speedup across multiple test classes, consider a shared `AbstractIntegrationTest` base class that holds the static container and the `@DynamicPropertySource` method.

---

## 7. Adding WireMock for GitHub API Tests

WireMock starts an embedded HTTP server that returns pre-configured responses. Point the application's GitHub API base URL at the WireMock server during tests.

### Gradle dependency

```groovy
testImplementation 'com.github.tomakehurst:wiremock-jre8:2.35.0'
```

### Example: `GitHubApiClientTest`

```java
@ExtendWith(MockitoExtension.class)
class GitHubApiClientTest {

    WireMockServer wireMock;
    GitHubApiClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        // Inject the WireMock base URL into the client
        client = new GitHubApiClient("http://localhost:" + wireMock.port(), mockAuthService);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void createInstallationToken_success_returnsToken() {
        wireMock.stubFor(post(urlEqualTo("/app/installations/42/access_tokens"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"token": "ghs_test123", "expires_at": "2099-01-01T00:00:00Z"}
                                """)));

        String token = client.createInstallationToken(42L);

        assertThat(token).isEqualTo("ghs_test123");
    }

    @Test
    void createInstallationToken_rateLimited_throwsRateLimitException() {
        wireMock.stubFor(post(urlEqualTo("/app/installations/42/access_tokens"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.createInstallationToken(42L))
                .isInstanceOf(RateLimitException.class);
    }
}
```

### Generating a test RSA key for JWT tests

```java
@BeforeAll
static void generateKeyPair() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair = gen.generateKeyPair();
    testPrivateKey = pair.getPrivate();
    testPublicKey = pair.getPublic();
}
```

Pass `testPrivateKey` to `GitHubAppAuthService` under test. Verify the JWT using the corresponding `testPublicKey` with a library like `nimbus-jose-jwt` (already a transitive dependency of the Spring Security stack).

---

## 8. Testing Guidelines and Conventions

### Test class setup

```java
// Unit test — no Spring context
@ExtendWith(MockitoExtension.class)
class MyServiceTest { ... }

// Repository slice — JPA context only
@DataJpaTest
class MyRepositoryTest { ... }

// Full integration test
@SpringBootTest
@Testcontainers
class MyControllerTest { ... }
```

### Naming convention

Use the pattern `methodName_condition_expectedResult`:

```java
parse_validHttpsUrl_extractsOwnerAndRepo()
getById_unknownId_throwsNotFoundException()
calculateF1_zeroPredictions_returnsZero()
```

This makes the test report self-documenting — a failing test name alone usually identifies the broken behavior.

### Assertions

Use AssertJ throughout. Prefer specific assertion methods over `isEqualTo(true)`:

```java
// Preferred
assertThat(result).isEqualTo("expected");
assertThat(list).hasSize(3).containsExactly("a", "b", "c");
assertThatThrownBy(() -> service.getById(-1L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("not found");

// Avoid
assertTrue(result.equals("expected"));
assertEquals(3, list.size());
```

### Parameterized tests

Use `@CsvSource` for simple scalar inputs:

```java
@ParameterizedTest
@CsvSource({
    "https://github.com/owner/repo, owner, repo",
    "https://github.com/org/project.git, org, project",
    "git@github.com:owner/repo.git, owner, repo"
})
void parse_variousUrlFormats_extractsOwnerAndRepo(String url, String owner, String repo) {
    ...
}
```

Use `@MethodSource` when inputs are objects or the setup is too verbose for inline CSV.

### No `Thread.sleep` in unit tests

If testing time-dependent behavior (token expiry, timeout logic), inject a `Clock` or a callback into the class under test and control it via a mock:

```java
// In production
new GitHubAppAuthService(clock, ...);

// In test
Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
GitHubAppAuthService service = new GitHubAppAuthService(fixedClock, ...);
```

### File system tests

Always use `@TempDir`:

```java
@Test
void writeWorkflowFile_createsFileAtExpectedPath(@TempDir Path workDir) {
    service.writeWorkflowFile(workDir, "workflow-content");
    assertThat(workDir.resolve(".github/workflows/benchmark.yml")).exists();
}
```

JUnit 5 handles deletion automatically. Never create test files in `src/test/resources` or `/tmp` without cleaning up.

### Mocking discipline

Only mock direct dependencies of the class under test. Do not mock the class under test itself, and do not mock value objects or simple data classes. If a test requires mocking more than 3-4 collaborators, consider whether the production class has too many responsibilities.

---

## 9. CI/CD Integration

### What to run on every PR

```bash
cd backend && ./gradlew test
```

This runs all unit and integration tests. The full suite completes in under 30 seconds on a standard CI runner (assuming no Testcontainers or WireMock tests yet). Once those are added, expect 1-3 minutes depending on container pull time.

### Suggested GitHub Actions step

```yaml
- name: Run backend tests
  working-directory: backend
  run: ./gradlew test

- name: Upload test report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-report
    path: backend/build/reports/tests/test/
```

The `if: always()` ensures the report is uploaded even when tests fail, so the HTML report is available for debugging without re-running locally.

### Test result publishing

Use the `dorny/test-reporter` action to show pass/fail counts directly in the PR checks UI:

```yaml
- name: Publish test results
  uses: dorny/test-reporter@v1
  if: always()
  with:
    name: Backend Tests
    path: backend/build/test-results/test/*.xml
    reporter: java-junit
```

### Separating slow tests

Once Testcontainers tests are added, consider tagging them with a JUnit 5 `@Tag("integration")` and running them on a separate CI job that only triggers on pushes to `main` (not every PR commit). This keeps the PR feedback loop fast.

```java
@Tag("integration")
@SpringBootTest
@Testcontainers
class ExemplarRepoControllerTest { ... }
```

```bash
# Unit tests only (fast, every commit)
./gradlew test -DexcludeTags=integration

# All tests including integration (on merge to main)
./gradlew test
```

### Coverage reports

If a coverage requirement is added in the future, use JaCoCo:

```groovy
// build.gradle
plugins {
    id 'jacoco'
}

jacocoTestReport {
    reports {
        xml.required = true  // for CI badge tools
        html.required = true
    }
}

test.finalizedBy jacocoTestReport
```

Report output: `backend/build/reports/jacoco/test/html/index.html`
