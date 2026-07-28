#!/usr/bin/env sh
set -eu
run_dir=${1:?run directory required}
test -s "$run_dir/run-metadata.json"
test -s "$run_dir/artifact-manifest.json"
test -s "$run_dir/summary.md"
jq -e '.runId and .scenarioId and .startedAt and .endedAt' "$run_dir/run-metadata.json" >/dev/null
jq -e '.artifacts | type == "array"' "$run_dir/artifact-manifest.json" >/dev/null

