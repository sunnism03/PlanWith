workflowVersion: 2
status: IN_PROGRESS
currentPhase: phase-8b
lastCompletedPhase: phase-8a
nextAction: "Start Phase 8B and execute the validated baseline plan without changing execution-affecting files"

baseline:
  generation: 0
  status: COMPLETED
  harnessCommit: a01ac11c7bc194e80d96eec96e56a284e3cbc8ef
  baselineCommit: 97432dc98f2ae8632151e4f301acb1831e79cf6d
  baselineEvidenceCommit: null
  baselineBranch: perf/chatbot-schedule
  sourceManifest: performance/plans/baseline-source-manifest.json
  frozenAt: "2026-07-29T02:29:42Z"
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
    status: COMPLETED
    startedAt: "2026-07-28T18:37:30Z"
    completedAt: "2026-07-29T02:29:42Z"
    outputs:
      - performance/results/EXP-01/baseline-plan.md
      - performance/plans/baseline-source-manifest.json
    blockers: []
  phase-8b:
    status: NOT_STARTED
    startedAt: null
    completedAt: null
    outputs: []
    blockers: []
  phase-9:
    status: NOT_STARTED
    startedAt: null
    completedAt: null
    outputs: []
    blockers: []
  phase-10:
    status: NOT_STARTED
    startedAt: null
    completedAt: null
    outputs: []
    blockers: []
  phase-11:
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
    status: IN_PROGRESS
    lastCompletedStep: phase-8a-completed
    baseline:
      status: NOT_RUN
      runId: null
      verdict: null
      artifactManifest: null
      summary: null
    git:
      branch: null
      worktree: null
      applicationBaseCommit: 97432dc98f2ae8632151e4f301acb1831e79cf6d
      branchStartCommit: null
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
      lastRunId: dry-20260729T022205Z-64356
      error: null
    rollback:
      status: NOT_REQUIRED
      command: null
      error: null
    retryCount: 0
    blockers: []
    outputs:
      - performance/plans/selected-experiments.md
      - performance/results/EXP-01/baseline-plan.md
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
  - performance/results/EXP-01/baseline-plan.md
  - performance/plans/baseline-source-manifest.json
