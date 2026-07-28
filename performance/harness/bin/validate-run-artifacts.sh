#!/usr/bin/env sh
set -eu
run_dir=${1:?run directory required}
test -s "$run_dir/run-metadata.json"
test -s "$run_dir/artifact-manifest.json"
test -s "$run_dir/summary.md"
jq -e '.runId and .scenarioId and .startedAt and .endedAt' "$run_dir/run-metadata.json" >/dev/null
jq -e '.artifacts | type == "array"' "$run_dir/artifact-manifest.json" >/dev/null
jq -r '.artifacts[] | [.relativePath,.sha256,.sizeBytes] | @tsv' "$run_dir/artifact-manifest.json" |
while IFS="$(printf '\t')" read -r relative expected_hash expected_size; do
  file="$run_dir/$relative"
  test -f "$file"
  actual_hash=$(shasum -a 256 "$file" | awk '{print $1}')
  actual_size=$(wc -c < "$file" | tr -d ' ')
  test "$actual_hash" = "$expected_hash"
  test "$actual_size" = "$expected_size"
done
