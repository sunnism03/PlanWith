#!/usr/bin/env sh
set -eu
root_dir=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
test -s "$root_dir/performance/harness/jmeter/chatbot-c1-dry-run.jmx"
test -s "$root_dir/performance/harness/mock/openai_mock.py"
test -s "$root_dir/performance/harness/harness-manifest.json"
"$root_dir/performance/harness/bin/validate-plans.sh"
"$root_dir/performance/harness/bin/scan-secrets.sh" "$root_dir/performance/harness"
jq -e '.dryRunStatus == "PASSED" and .secretScanStatus == "PASSED"' \
  "$root_dir/performance/harness/harness-manifest.json" >/dev/null

