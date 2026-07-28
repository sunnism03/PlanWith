#!/usr/bin/env sh
set -eu
file=${1:?comparison file required}
test -s "$file"
for term in "Comparison eligibility" "p95" "p99" "throughput" "Final verdict"; do
  grep -q "$term" "$file"
done

