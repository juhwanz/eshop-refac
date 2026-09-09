#!/usr/bin/env bash

set -euo pipefail

base_url="${BASE_URL:-http://localhost:8080/api}"
admin_email="${ADMIN_EMAIL:?ADMIN_EMAIL is required}"
admin_password="${ADMIN_PASSWORD:?ADMIN_PASSWORD is required}"
product_name="${PRODUCT_NAME:-k6-isolated-product}"
product_price="${PRODUCT_PRICE:-10000}"
product_stock="${PRODUCT_STOCK:-1000}"

if ! [[ "$product_price" =~ ^[1-9][0-9]*$ ]] || ((product_price < 100)); then
    echo "PRODUCT_PRICE must be an integer greater than or equal to 100" >&2
    exit 1
fi
if ! [[ "$product_stock" =~ ^[1-9][0-9]*$ ]]; then
    echo "PRODUCT_STOCK must be a positive integer" >&2
    exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

login_payload="$(jq -cn --arg email "$admin_email" --arg password "$admin_password" '{email:$email,password:$password}')"
login_status="$(curl -sS -o "$temp_dir/login.json" -w '%{http_code}' -X POST "$base_url/users/login" -H 'Content-Type: application/json' -d "$login_payload")"
if [[ "$login_status" != "200" ]]; then
    echo "admin login failed: HTTP ${login_status}" >&2
    exit 1
fi

access_token="$(jq -r '.data.accessToken // empty' "$temp_dir/login.json")"
if [[ -z "$access_token" ]]; then
    echo "admin login response did not contain an access token" >&2
    exit 1
fi

product_payload="$(jq -cn --arg name "$product_name" --argjson price "$product_price" --argjson stockQuantity "$product_stock" '{name:$name,price:$price,stockQuantity:$stockQuantity}')"
product_status="$(curl -sS -o "$temp_dir/product.json" -w '%{http_code}' -X POST "$base_url/products" -H 'Content-Type: application/json' -H "Authorization: Bearer ${access_token}" -d "$product_payload")"
if [[ "$product_status" != "201" ]]; then
    echo "product creation failed: HTTP ${product_status}" >&2
    exit 1
fi

product_id="$(jq -r '.data // empty' "$temp_dir/product.json")"
if [[ -z "$product_id" ]]; then
    echo "product creation response did not contain a product ID" >&2
    exit 1
fi

echo "prepared product ID ${product_id} with stock ${product_stock}"
