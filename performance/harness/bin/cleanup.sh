#!/usr/bin/env sh
set -eu
port=${1:?redis port required}
redis-cli -p "$port" --scan --pattern 'chatbot:*' | while IFS= read -r key; do
  test -z "$key" || redis-cli -p "$port" DEL "$key" >/dev/null
done
redis-cli -p "$port" --scan --pattern 'chatbot_token:*' | while IFS= read -r key; do
  test -z "$key" || redis-cli -p "$port" DEL "$key" >/dev/null
done

