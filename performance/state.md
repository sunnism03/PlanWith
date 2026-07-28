workflowVersion: 2
status: IN_PROGRESS
currentPhase: phase-7
lastCompletedPhase: phase-6-5
nextAction: "Create performance/plans/selected-experiments.md"

baseline:
  generation: 0
  status: NOT_STARTED
  harnessCommit: 927ea7daefe2fe87607c30e1c9a1b340d30e28e4
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
    status: IN_PROGRESS
    startedAt: "2026-07-28T18:01:22Z"
    completedAt: null
    outputs: []
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

selectedExperimentIds: []
experiments: {}
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
