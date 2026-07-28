#!/usr/bin/env sh
set -eu
output=${1:?output required}
run_id=${2:?run id required}
started_at=${3:?started at required}
ended_at=${4:?ended at required}
jq -n \
  --arg experimentId "DRY-RUN" \
  --arg scenarioId "C1" \
  --arg runId "$run_id" \
  --arg startedAt "$started_at" \
  --arg endedAt "$ended_at" \
  --arg openAiMode "MOCK" \
  '{experimentId:$experimentId,scenarioId:$scenarioId,runId:$runId,baselineGeneration:0,baselineCommit:null,harnessCommit:null,jmeterScenarioHash:null,activeProfile:"dev",openAiMode:$openAiMode,threads:1,rampUp:1,duration:0,testRunId:$runId,startedAt:$startedAt,endedAt:$endedAt}' > "$output"

