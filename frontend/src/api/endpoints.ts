import client from './client'
import type {
  ExemplarRepo,
  BenchmarkSuite,
  SuitePr,
  Bot,
  BenchmarkRun,
  ReplayPr,
  ReviewComment,
  OriginalComment,
  Grading,
  GoldenDatasetEntry,
  RunProgress,
  RunReport,
  ComparisonReport,
  SetupStatus,
  SetupInstructions,
  GitHubPr,
  RateLimitState,
  BotTrend,
  SignificanceTest,
  UngradedComment,
  BulkGradeRequest,
  BulkGradeResult,
  GradingProgress,
  IssueType,
  BotSnapshot,
  ScheduledRun,
  CreateScheduledRunRequest,
} from './types'

// Repos
export const getRepos = () =>
  client.get<ExemplarRepo[]>('/repos').then((r) => r.data)

export const createRepo = (data: {
  name: string
  githubUrl: string
  mirrorOrg?: string
  mirrorRepoName?: string
}) => client.post<ExemplarRepo>('/repos', data).then((r) => r.data)

export const getRepo = (id: number) =>
  client.get<ExemplarRepo>(`/repos/${id}`).then((r) => r.data)

export const updateRepo = (
  id: number,
  data: Partial<ExemplarRepo>,
) => client.put<ExemplarRepo>(`/repos/${id}`, data).then((r) => r.data)

export const deleteRepo = (id: number) =>
  client.delete(`/repos/${id}`)

export const getRepoPrs = (id: number, state = 'all') =>
  client.get<GitHubPr[]>(`/repos/${id}/prs`, { params: { state } }).then((r) => r.data)

export const syncRepo = (id: number) =>
  client.post(`/repos/${id}/sync`)

// Suites
export const getSuites = (repoId?: number) =>
  client
    .get<BenchmarkSuite[]>('/suites', { params: repoId ? { repoId } : undefined })
    .then((r) => r.data)

export const createSuite = (data: {
  exemplarRepoId: number
  name: string
  description?: string
}) => client.post<BenchmarkSuite>('/suites', data).then((r) => r.data)

export const getSuite = (id: number) =>
  client.get<BenchmarkSuite>(`/suites/${id}`).then((r) => r.data)

export const updateSuite = (id: number, data: Partial<BenchmarkSuite>) =>
  client.put<BenchmarkSuite>(`/suites/${id}`, data).then((r) => r.data)

export const deleteSuite = (id: number) =>
  client.delete(`/suites/${id}`)

export const getSuitePrs = (id: number) =>
  client.get<SuitePr[]>(`/suites/${id}/prs`).then((r) => r.data)

export const addSuitePr = (suiteId: number, originalPrNumber: number) =>
  client
    .post<SuitePr>(`/suites/${suiteId}/prs`, { originalPrNumber })
    .then((r) => r.data)

export const removeSuitePr = (suiteId: number, prId: number) =>
  client.delete(`/suites/${suiteId}/prs/${prId}`)

// Bots
export const getBots = () =>
  client.get<Bot[]>('/bots').then((r) => r.data)

export const createBot = (data: {
  name: string
  description?: string
  workflowFileName?: string
  workflowContent?: string
  waitStrategy: string
  timeoutSeconds: number
}) => client.post<Bot>('/bots', data).then((r) => r.data)

export const getBot = (id: number) =>
  client.get<Bot>(`/bots/${id}`).then((r) => r.data)

export const updateBot = (id: number, data: Partial<Bot>) =>
  client.put<Bot>(`/bots/${id}`, data).then((r) => r.data)

export const deleteBot = (id: number) =>
  client.delete(`/bots/${id}`)

// Runs
export const getRuns = (suiteId?: number) =>
  client
    .get<BenchmarkRun[]>('/runs', { params: suiteId ? { suiteId } : undefined })
    .then((r) => r.data)

export const createRun = (data: {
  suiteId: number
  name: string
  botIds: number[]
  concurrency: number
  goldenDatasetEnabled: boolean
}) => client.post<BenchmarkRun>('/runs', data).then((r) => r.data)

export const getRun = (id: number) =>
  client.get<BenchmarkRun>(`/runs/${id}`).then((r) => r.data)

export const getRunProgress = (id: number) =>
  client.get<RunProgress>(`/runs/${id}/progress`).then((r) => r.data)

export const cancelRun = (id: number) =>
  client.post(`/runs/${id}/cancel`)

export const getReplayPrs = (runId: number) =>
  client.get<ReplayPr[]>(`/runs/${runId}/replay-prs`).then((r) => r.data)

export const cleanupRun = (id: number) =>
  client.post(`/runs/${id}/cleanup`)

// Comments
export const getReplayPrComments = (replayPrId: number) =>
  client
    .get<ReviewComment[]>(`/replay-prs/${replayPrId}/comments`)
    .then((r) => r.data)

export const getSuitePrOriginalComments = (suitePrId: number) =>
  client
    .get<OriginalComment[]>(`/suite-prs/${suitePrId}/original-comments`)
    .then((r) => r.data)

// Grading
export const getGradings = (commentId: number, type = 'REVIEW') =>
  client
    .get<Grading[]>(`/comments/${commentId}/gradings`, { params: { type } })
    .then((r) => r.data)

export const createGrading = (data: {
  commentId: number
  commentTableType: string
  graderType: string
  graderId?: string
  verdict: string
  severity?: string
  stars: number
  flagged: boolean
  notes?: string
}) => client.post<Grading>('/gradings', data).then((r) => r.data)

export const updateGrading = (id: number, data: Partial<Grading>) =>
  client.put<Grading>(`/gradings/${id}`, data).then((r) => r.data)

export const deleteGrading = (id: number) =>
  client.delete(`/gradings/${id}`)

// Golden Dataset
export const getGoldenDataset = (activeOnly = true) =>
  client
    .get<GoldenDatasetEntry[]>('/golden-dataset', { params: { activeOnly } })
    .then((r) => r.data)

export const promoteToGolden = (data: {
  commentId: number
  commentTableType: string
  issueType?: string
  description?: string
}) =>
  client.post<GoldenDatasetEntry>('/golden-dataset/promote', data).then((r) => r.data)

export const updateGoldenEntry = (id: number, data: Partial<GoldenDatasetEntry>) =>
  client.put<GoldenDatasetEntry>(`/golden-dataset/${id}`, data).then((r) => r.data)

export const deleteGoldenEntry = (id: number) =>
  client.delete(`/golden-dataset/${id}`)

export const exportGoldenDataset = () =>
  client.get('/golden-dataset/export', { responseType: 'blob' }).then((r) => r.data)

export const importGoldenDataset = (content: string) =>
  client.post('/golden-dataset/import', content, {
    headers: { 'Content-Type': 'text/plain' },
  })

// Reports
export const getRunReport = (runId: number) =>
  client.get<RunReport>(`/reports/runs/${runId}`).then((r) => r.data)

export const getComparisonReport = (runId: number) =>
  client.get<ComparisonReport>(`/reports/runs/${runId}/comparison`).then((r) => r.data)

// Setup
export const getSetupStatus = () =>
  client.get<SetupStatus>('/setup/status').then((r) => r.data)

export const getSetupInstructions = () =>
  client.get<SetupInstructions>('/setup/instructions').then((r) => r.data)

export const getSetupInstallations = () =>
  client.get('/setup/installations').then((r) => r.data)

// Rate limit
export const getRateLimit = () => client.get<RateLimitState>('/setup/rate-limit').then(r => r.data)

// Trend + significance
export const getBotTrend = (botId: number, suiteId: number, limit = 10) =>
  client.get<BotTrend>(`/reports/bots/${botId}/trend`, { params: { suiteId, limit } }).then(r => r.data)
export const getSignificance = (runAId: number, runBId: number) =>
  client.get<SignificanceTest>(`/reports/runs/${runAId}/significance`, { params: { compareRunId: runBId } }).then(r => r.data)

// Grading queue
export const getGradingQueue = (params: { runId?: number; suiteId?: number; limit?: number }) =>
  client.get<UngradedComment[]>('/grading-queue', { params }).then(r => r.data)
export const bulkGrade = (req: BulkGradeRequest) =>
  client.post<BulkGradeResult>('/gradings/bulk', req).then(r => r.data)
export const getGradingProgress = (params: { runId?: number; suiteId?: number }) =>
  client.get<GradingProgress>('/grading-progress', { params }).then(r => r.data)

// Exports
export const exportRunCsv = (runId: number) => `/api/exports/runs/${runId}/csv`  // direct download URL
export const exportRunSarif = (runId: number) => `/api/exports/runs/${runId}/sarif`
export const exportGoldenDatasetCsv = () => '/api/exports/golden-dataset/csv'

// Bot snapshots
export const getBotSnapshots = (runId: number) =>
  client.get<BotSnapshot[]>(`/runs/${runId}/bot-snapshots`).then(r => r.data)

// Issue types
export const getIssueTypes = (activeOnly = true) =>
  client.get<IssueType[]>('/issue-types', { params: { activeOnly } }).then(r => r.data)
export const createIssueType = (data: Partial<IssueType>) =>
  client.post<IssueType>('/issue-types', data).then(r => r.data)
export const updateIssueType = (id: number, data: Partial<IssueType>) =>
  client.put<IssueType>(`/issue-types/${id}`, data).then(r => r.data)
export const deleteIssueType = (id: number) => client.delete(`/issue-types/${id}`)

// Scheduled runs
export const getScheduledRuns = (suiteId?: number) =>
  client.get<ScheduledRun[]>('/scheduled-runs', { params: suiteId ? { suiteId } : {} }).then(r => r.data)
export const createScheduledRun = (data: CreateScheduledRunRequest) =>
  client.post<ScheduledRun>('/scheduled-runs', data).then(r => r.data)
export const updateScheduledRun = (id: number, data: Partial<ScheduledRun>) =>
  client.put<ScheduledRun>(`/scheduled-runs/${id}`, data).then(r => r.data)
export const deleteScheduledRun = (id: number) => client.delete(`/scheduled-runs/${id}`)
export const triggerScheduledRun = (id: number) =>
  client.post<BenchmarkRun>(`/scheduled-runs/${id}/trigger`).then(r => r.data)
