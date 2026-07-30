#!/usr/bin/env sh
set -eu

base_url=${1:?base URL required}
users_csv=${2:?users CSV required}
year=${3:?scenario year required}
month=${4:?scenario month required}
phase=${5:?phase required: before or after}
output=${6:?output path required}

case "$phase" in
  before|after) ;;
  *) echo "phase must be before or after" >&2; exit 1 ;;
esac
case "$year:$month" in
  *[!0-9:]*) echo "year and month must be numeric" >&2; exit 1 ;;
esac

entries=$(mktemp "${TMPDIR:-/tmp}/planwith-db-postcondition.XXXXXX")
trap 'rm -f "$entries"' EXIT INT TERM
: > "$entries"

while IFS=, read -r user_alias jwt user_id category_id group_id group_event_id; do
  user_alias=$(printf '%s' "$user_alias" | tr -d '"')
  jwt=$(printf '%s' "$jwt" | tr -d '"')
  user_id=$(printf '%s' "$user_id" | tr -d '"')
  test -n "$user_alias"
  test -n "$jwt"
  test -n "$user_id"

  events=$(curl -fsS \
    -H "Authorization: $jwt" \
    "$base_url/events?year=$year&month=$month")
  todos=$(curl -fsS \
    -H "Authorization: $jwt" \
    "$base_url/todos/monthly?year=$year&month=$month")
  event_count=$(printf '%s' "$events" | jq -e '.data | if type == "array" then length else error("events data is not an array") end')
  todo_count=$(printf '%s' "$todos" | jq -e '.data | if type == "array" then length else error("todos data is not an array") end')

  jq -cn \
    --arg userAlias "$user_alias" \
    --arg userId "$user_id" \
    --argjson events "$event_count" \
    --argjson todos "$todo_count" \
    '{userAlias:$userAlias,userId:$userId,eventCount:$events,todoCount:$todos,
      status:(if $events == 0 and $todos == 0 then "PASS" else "FAIL" end)}' >> "$entries"
done < "$users_csv"

test -s "$entries"
checked_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq -s \
  --arg phase "$phase" \
  --arg year "$year" \
  --arg month "$month" \
  --arg checkedAt "$checked_at" \
  '{phase:$phase,year:$year,month:$month,checkedAt:$checkedAt,users:.,
    status:(if all(.[]; .status == "PASS") then "PASS" else "FAIL" end)}' \
  "$entries" > "$output"
jq -e '.status == "PASS"' "$output" >/dev/null

rm -f "$entries"
trap - EXIT INT TERM
