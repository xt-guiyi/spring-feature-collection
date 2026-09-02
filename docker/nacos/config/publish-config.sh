#!/usr/bin/env bash
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-http://104.199.166.106:8848}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-e7e276aa-ddfa-4a1c-85b3-b676cba8d829}"
NACOS_GROUP="${NACOS_GROUP:-SPRING_FEATURE_COLLECTION}"
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-123456}"
CONFIG_DIR="$(cd "$(dirname "$0")" && pwd)"

auth_response="$(curl --fail-with-body --silent --show-error \
  --request POST "${NACOS_ADDR}/nacos/v1/auth/login" \
  --data-urlencode "username=${NACOS_USERNAME}" \
  --data-urlencode "password=${NACOS_PASSWORD}")"
access_token="$(printf '%s' "$auth_response" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
if [[ -z "$access_token" ]]; then
  echo "Nacos 登录成功但未返回 accessToken" >&2
  exit 1
fi

for config_file in "$CONFIG_DIR"/*.yaml; do
  data_id="$(basename "$config_file")"
  echo "发布 Nacos 配置: ${data_id}"
  curl --fail-with-body --silent --show-error \
    --request POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${NACOS_GROUP}" \
    --data-urlencode "tenant=${NACOS_NAMESPACE}" \
    --data-urlencode "accessToken=${access_token}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@${config_file}"
  echo
done
