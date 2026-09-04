#!/usr/bin/env bash

# 本地 Playground 的 Sentinel 和 XXL-JOB 回调反向隧道。
# 保持本脚本所在终端运行；关闭终端后隧道会断开。

set -euo pipefail

SERVER_USER="1661219752"
SERVER_HOST="104.155.229.164"
SSH_KEY="${HOME}/.ssh/id_ed25519"

if [[ ! -f "${SSH_KEY}" ]]; then
  echo "找不到 SSH 私钥：${SSH_KEY}" >&2
  exit 1
fi

echo "正在建立反向隧道："
echo "  Sentinel  ${SERVER_HOST}:8720  -> 本机 127.0.0.1:8720"
echo "  XXL-JOB   ${SERVER_HOST}:19998 -> 本机 127.0.0.1:19999"
echo "按 Ctrl+C 关闭隧道。"

exec ssh \
  -N -T \
  -i "${SSH_KEY}" \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=60 \
  -o ServerAliveCountMax=3 \
  -R "0.0.0.0:8720:127.0.0.1:8720" \
  -R "0.0.0.0:19998:127.0.0.1:19999" \
  "${SERVER_USER}@${SERVER_HOST}"
