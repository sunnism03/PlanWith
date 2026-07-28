#!/usr/bin/env sh
set -eu
root_dir=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
for file in \
  context-verification.md chatbot-code-map.md chatbot-bottleneck-hypotheses.md \
  group-event-crud-code-map.md group-event-concurrency-hypotheses.md \
  observability-checklist.md jmeter-test-design.md
do
  test -s "$root_dir/performance/plans/$file"
done
grep -q 'scenarioId' "$root_dir/performance/plans/jmeter-test-design.md"
grep -q 'hypothesisId' "$root_dir/performance/plans/jmeter-test-design.md"

