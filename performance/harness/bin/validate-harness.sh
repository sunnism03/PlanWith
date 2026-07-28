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
manifest="$root_dir/performance/harness/harness-manifest.json"
jq -r '.jmeterPlanHashes | to_entries[] | [.key,.value] | @tsv' "$manifest" |
while IFS="$(printf '\t')" read -r path expected; do
  actual=$(shasum -a 256 "$root_dir/performance/harness/$path" | awk '{print $1}')
  test "$actual" = "$expected"
done
jq -r '.scriptHashes | to_entries[] | [.key,.value] | @tsv' "$manifest" |
while IFS="$(printf '\t')" read -r path expected; do
  actual=$(shasum -a 256 "$root_dir/performance/harness/$path" | awk '{print $1}')
  test "$actual" = "$expected"
done
actual_mock=$(shasum -a 256 "$root_dir/performance/harness/mock/openai_mock.py" | awk '{print $1}')
test "$actual_mock" = "$(jq -r '.mockConfigurationHash' "$manifest")"
