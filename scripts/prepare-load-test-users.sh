#!/usr/bin/env bash

set -euo pipefail

base_url="${BASE_URL:-http://localhost:8080/api}"
user_count="${USER_COUNT:-30}"
email_prefix="${USER_EMAIL_PREFIX:-load-user}"
email_domain="${USER_EMAIL_DOMAIN:-example.com}"
password="${USER_PASSWORD:?USER_PASSWORD is required}"
output_file="${K6_USERS_FILE:-load-test.users.json}"

if ! [[ "$user_count" =~ ^[1-9][0-9]*$ ]]; then
    echo "USER_COUNT must be a positive integer" >&2
    exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
temp_users="$temp_dir/users.jsonl"

for ((index = 1; index <= user_count; index++)); do
    suffix="$(printf '%02d' "$index")"
    email="${email_prefix}-${suffix}@${email_domain}"
    username="load${suffix}"
    payload="$(jq -cn --arg email "$email" --arg password "$password" --arg username "$username" '{email:$email,password:$password,username:$username}')"
    response_file="$temp_dir/signup-${suffix}.json"
    status="$(curl -sS -o "$response_file" -w '%{http_code}' -X POST "$base_url/users/signup" -H 'Content-Type: application/json' -d "$payload")"

    if [[ "$status" != "201" ]] && ! jq -e '.code == "EMAIL_DUPLICATION"' "$response_file" >/dev/null 2>&1; then
        echo "failed to prepare test user ${suffix}: HTTP ${status}" >&2
        exit 1
    fi

    login_payload="$(jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}')"
    login_status="$(curl -sS -o "$temp_dir/login-${suffix}.json" -w '%{http_code}' -X POST "$base_url/users/login" -H 'Content-Type: application/json' -d "$login_payload")"
    if [[ "$login_status" != "200" ]]; then
        echo "failed to verify test user ${suffix}: HTTP ${login_status}" >&2
        exit 1
    fi

    jq -cn --arg email "$email" --arg password "$password" '{email:$email,password:$password}' >> "$temp_users"
done

jq -s '.' "$temp_users" > "$output_file"
chmod 600 "$output_file"
echo "prepared ${user_count} users in ${output_file}"
