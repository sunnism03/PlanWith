#!/usr/bin/env sh
set -eu
run_dir=${1:?run directory required}
run_id=${2:?run id required}
output="$run_dir/artifact-manifest.json"
tmp="$run_dir/.artifact-entries.jsonl"
: > "$tmp"
for file in "$run_dir"/*; do
  test -f "$file" || continue
  case "$(basename "$file")" in
    artifact-manifest.json|.artifact-entries.jsonl) continue ;;
  esac
  relative=$(basename "$file")
  hash=$(shasum -a 256 "$file" | awk '{print $1}')
  size=$(wc -c < "$file" | tr -d ' ')
  created=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  jq -cn --arg runId "$run_id" --arg type "$relative" --arg path "$relative" \
    --arg sha "$hash" --argjson size "$size" --arg created "$created" \
    '{runId:$runId,artifactType:$type,relativePath:$path,sha256:$sha,sizeBytes:$size,createdAt:$created,validationStatus:"VALID"}' >> "$tmp"
done
jq -s --arg runId "$run_id" '{runId:$runId,artifacts:.}' "$tmp" > "$output"
rm "$tmp"

