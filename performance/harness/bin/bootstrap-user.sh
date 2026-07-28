#!/usr/bin/env sh
set -eu
base_url=${1:?base URL required}
run_id=${2:?run id required}
runtime_dir=${3:?runtime directory required}

suffix=$(printf '%s' "$run_id" | shasum -a 256 | cut -c1-10)
email="dry-${suffix}@example.test"
password="DryRun!${suffix}"
nickname="dry-${suffix}"

signup=$(jq -cn --arg email "$email" --arg password "$password" --arg nickname "$nickname" \
  '{email:$email,password:$password,nickname:$nickname}')
signup_response=$(curl -fsS -H 'Content-Type: application/json' -d "$signup" "$base_url/user/signup")
user_id=$(printf '%s' "$signup_response" | jq -er '.data.userId')
category_id=$(printf '%s' "$signup_response" | jq -er '.data.categoryId')

login=$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}')
login_response=$(curl -fsS -H 'Content-Type: application/json' -d "$login" "$base_url/user/login")
authorization=$(printf '%s' "$login_response" | jq -er '.access_token')

printf '%s' "$authorization" > "$runtime_dir/authorization.runtime"
printf '"dry-user","%s","%s","%s","",""\n' "$authorization" "$user_id" "$category_id" > "$runtime_dir/users.csv"
