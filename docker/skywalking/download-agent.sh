#!/usr/bin/env bash

set -euo pipefail

AGENT_VERSION="9.6.0"
AGENT_ARCHIVE="apache-skywalking-java-agent-${AGENT_VERSION}.tgz"
AGENT_URL="https://archive.apache.org/dist/skywalking/java-agent/${AGENT_VERSION}/${AGENT_ARCHIVE}"
AGENT_SHA512="64346286924aafcbd5e44358e4fd720a52900192bc5a32846283feee728aad90454ac8574683599b7fe4d59d587a4c1ae58744ed01028d5010847b1277906afa"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_DIR="${SCRIPT_DIR}/agent"
TEMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

curl --fail --location --output "${TEMP_DIR}/${AGENT_ARCHIVE}" "${AGENT_URL}"
echo "${AGENT_SHA512}  ${TEMP_DIR}/${AGENT_ARCHIVE}" | shasum -a 512 --check
tar -xzf "${TEMP_DIR}/${AGENT_ARCHIVE}" -C "${TEMP_DIR}"

rm -rf "${AGENT_DIR}"
mv "${TEMP_DIR}/skywalking-agent" "${AGENT_DIR}"

echo "SkyWalking Java Agent 已安装到：${AGENT_DIR}/skywalking-agent.jar"
