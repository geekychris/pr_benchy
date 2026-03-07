export interface ExemplarRepo {
  id: number
  name: string
  githubUrl: string
  owner: string
  repoName: string
  mirrorOrg: string | null
  mirrorRepoName: string | null
  mirrorRepoUrl: string | null
  defaultBranch: string
  lastSyncedAt: string | null
  syncStatus: string
  createdAt: string
  updatedAt: string
}

export interface BenchmarkSuite {
  id: number
  name: string
  description: string | null
  exemplarRepo: ExemplarRepo
  createdAt: string
  updatedAt: string
}

export interface SuitePr {
  id: number
  suite: { id: number; name: string }
  originalPrNumber: number
  title: string | null
  baseCommitSha: string | null
  headCommitSha: string | null
  baseBranch: string | null
  author: string | null
  mergedAt: string | null
  filesChanged: number | null
  additions: number | null
  deletions: number | null
  createdAt: string
}

export interface Bot {
  id: number
  name: string
  description: string | null
  workflowFileName: string | null
  workflowContent: string | null
  waitStrategy: string
  timeoutSeconds: number
  createdAt: string
  updatedAt: string
}

export interface BenchmarkRun {
  id: number
  suite: BenchmarkSuite
  name: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  concurrency: number
  goldenDatasetEnabled: boolean
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
  bots: Bot[]
  createdAt: string
}

export interface ReplayPr {
  id: number
  suitePr: SuitePr
  bot: Bot
  mirrorPrNumber: number | null
  mirrorPrUrl: string | null
  baseBranch: string | null
  headBranch: string | null
  status: string
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
}

export interface ReviewComment {
  id: number
  source: 'BOT' | 'ORIGINAL_HUMAN'
  bot: Bot | null
  commentType: 'REVIEW_COMMENT' | 'PR_COMMENT' | 'REVIEW'
  filePath: string | null
  lineNumber: number | null
  body: string
  githubCreatedAt: string | null
}

export interface OriginalComment {
  id: number
  author: string | null
  commentType: string
  filePath: string | null
  lineNumber: number | null
  body: string
  githubCreatedAt: string | null
}

export interface Grading {
  id: number
  commentId: number
  commentTableType: string
  graderType: string
  graderId: string | null
  verdict: 'VALID' | 'INVALID' | 'DUPLICATE' | 'NEEDS_REVIEW'
  severity: string | null
  stars: number
  flagged: boolean
  notes: string | null
  createdAt: string
}

export interface GoldenDatasetEntry {
  id: number
  suitePr: SuitePr
  filePath: string | null
  lineNumber: number | null
  issueType: string | null
  description: string | null
  canonicalBody: string | null
  active: boolean
  includedByDefault: boolean
  createdAt: string
}

export interface RunProgress {
  runId: number
  status: string
  total: number
  pending: number
  creating: number
  waiting: number
  collecting: number
  completed: number
  failed: number
}

export interface BotStats {
  botId: number
  botName: string
  totalComments: number
  reviewComments: number
  prComments: number
  reviews: number
  validCount: number
  invalidCount: number
  duplicateCount: number
  needsReviewCount: number
  ungradedCount: number
}

export interface RunReport {
  runId: number
  runName: string
  status: string
  startedAt: string | null
  completedAt: string | null
  totalReplayPrs: number
  completedReplayPrs: number
  failedReplayPrs: number
  botStats: BotStats[]
}

export interface BotComparison {
  botId: number
  botName: string
  truePositives: number
  falseNegatives: number
  falsePositives: number
  precision: number
  recall: number
  f1Score: number
}

export interface ComparisonReport {
  runId: number
  goldenEntryCount: number
  botComparisons: BotComparison[]
}

export interface SetupStatus {
  appId: string
  installationId: string
  jwtOk: boolean
  tokenOk: boolean
  defaultMirrorOrg: string
  errorMessage: string | null
}

export interface SetupInstructions {
  requiredPermissions: string[]
  requiredEvents: string[]
  setupSteps: string[]
}

export interface GitHubPr {
  number: number
  title: string
  state: string
  author: string
  mergedAt: string | null
  filesChanged: number
  additions: number
  deletions: number
  createdAt: string
}

export interface RateLimitState { limit: number; remaining: number; resetAt: string; }
export interface TrendDataPoint { runId: number; runName: string; startedAt: string; precision: number; recall: number; f1Score: number; totalComments: number; truePositives: number; falsePositives: number; falseNegatives: number; }
export interface BotTrend { botId: number; botName: string; dataPoints: TrendDataPoint[]; }
export interface SignificanceTest { runAId: number; runAName: string; runBId: number; runBName: string; runAF1: number; runBF1: number; pValue: number; significant: boolean; interpretation: string; }
export interface UngradedComment { commentId: number; commentTableType: string; replayPrId: number | null; suitePrId: number; originalPrNumber: number; prTitle: string | null; botName: string | null; commentType: string; filePath: string | null; lineNumber: number | null; body: string; stars: number | null; flagged: boolean | null; }
export interface BulkGradeRequest { commentIds: number[]; commentTableType: string; verdict: string; severity: string | null; stars: number | null; flagged: boolean | null; notes: string | null; graderId: string | null; }
export interface BulkGradeResult { successCount: number; skippedCount: number; errors: string[]; }
export interface GradingProgress { totalComments: number; gradedComments: number; ungradedComments: number; percentComplete: number; validCount: number; invalidCount: number; duplicateCount: number; needsReviewCount: number; flaggedCount: number; }
export interface IssueType { id: number; code: string; name: string; description: string | null; category: string | null; severityHint: string | null; active: boolean; createdAt: string; }
export interface BotSnapshot { id: number; botId: number; name: string; workflowFileName: string | null; waitStrategy: string; timeoutSeconds: number; snapshottedAt: string; }
export interface ScheduledRun { id: number; name: string; suite: BenchmarkSuite; botIds: string; cronExpression: string; concurrency: number; goldenDatasetEnabled: boolean; enabled: boolean; lastRunAt: string | null; lastRunStatus: string | null; nextRunAt: string | null; createdAt: string; }
export interface CreateScheduledRunRequest { name: string; suiteId: number; botIds: number[]; cronExpression: string; concurrency: number; goldenDatasetEnabled: boolean; }
