workflowVersion: 2
status: COMPLETED
currentPhase: phase-8a
lastCompletedPhase: phase-7
nextAction: "Start the Baseline Goal at Phase 8A and create performance/results/EXP-01/baseline-plan.md"

baseline:
  generation: 0
  status: NOT_STARTED
  harnessCommit: 6776ac4482903dde42ee3db6c69649abb7ddabab
  baselineCommit: null
  baselineEvidenceCommit: null
  baselineBranch: null
  sourceManifest: null
  frozenAt: null
  invalidatedReason: null

phases:
  phase-1:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/context-verification.md
    blockers: []
  phase-2:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/chatbot-code-map.md
    blockers: []
  phase-3:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/chatbot-bottleneck-hypotheses.md
    blockers: []
  phase-4a:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/group-event-crud-code-map.md
    blockers: []
  phase-4b:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/group-event-concurrency-hypotheses.md
    blockers: []
  phase-5:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/observability-checklist.md
    blockers: []
  phase-6:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T17:20:04Z"
    outputs:
      - performance/plans/jmeter-test-design.md
    blockers: []
  phase-6-5:
    status: COMPLETED
    startedAt: "2026-07-28T17:20:04Z"
    completedAt: "2026-07-28T18:01:22Z"
    outputs:
      - performance/harness/harness-manifest.json
      - performance/plans/test-harness-report.md
      - performance/harness/jmeter/chatbot-c1-dry-run.jmx
    blockers: []
  phase-7:
    status: COMPLETED
    startedAt: "2026-07-28T18:01:22Z"
    completedAt: "2026-07-28T18:01:22Z"
    outputs:
      - performance/plans/selected-experiments.md
    blockers: []
  phase-8a:
    status: NOT_STARTED
    startedAt: null
    completedAt: null
    outputs: []
    blockers: []
  phase-8b:
    status: NOT_STARTED
    startedAt: null
    completedAt: null
    outputs: []
    blockers: []

selectedExperimentIds:
  - EXP-01
experiments:
  EXP-01:
    hypothesisId:
      - CHAT-PERF-01
      - CHAT-REL-01
    scenarioId: C1
    status: NOT_STARTED
    lastCompletedStep: phase-7-selection
    baseline:
      status: NOT_RUN
      runId: null
      verdict: null
      artifactManifest: null
      summary: null
    git:
      branch: null
      worktree: null
      baseCommit: null
      planCommit: null
      improvementCommit: null
      reportCommit: null
    improvement:
      status: NOT_STARTED
      changeScopeStatus: null
      planPath: null
    after:
      status: NOT_RUN
      runId: null
      artifactManifest: null
      summary: null
    comparison:
      status: NOT_STARTED
      verdict: null
      path: null
    cleanup:
      status: NOT_REQUIRED
      lastRunId: dry-20260728T180114Z-35705
      error: null
    rollback:
      status: NOT_REQUIRED
      command: null
      error: null
    retryCount: 0
    blockers: []
    outputs:
      - performance/plans/selected-experiments.md
globalBlockers: []
completedOutputs:
  - performance/plans/context-verification.md
  - performance/plans/chatbot-code-map.md
  - performance/plans/chatbot-bottleneck-hypotheses.md
  - performance/plans/group-event-crud-code-map.md
  - performance/plans/group-event-concurrency-hypotheses.md
  - performance/plans/observability-checklist.md
  - performance/plans/jmeter-test-design.md
  - performance/harness/harness-manifest.json
  - performance/plans/test-harness-report.md
  - performance/plans/selected-experiments.md
