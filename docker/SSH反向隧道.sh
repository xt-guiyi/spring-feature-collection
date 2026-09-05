#!/usr/bin/env bash

# Sentinel/XXL-JOB 使用反向转发，SkyWalking OAP/UI 使用本地转发。
# 保持本脚本所在终端运行；关闭终端后隧道会断开。

set -euo pipefail

SERVER_USER="1661219752"
SERVER_HOST="104.155.229.164"
SSH_KEY="${HOME}/.ssh/id_ed25519"

if [[ ! -f "${SSH_KEY}" ]]; then
  echo "找不到 SSH 私钥：${SSH_KEY}" >&2
  exit 1
fi

echo "正在建立 SSH 隧道："
echo "  Sentinel  ${SERVER_HOST}:8720  -> 本机 127.0.0.1:8720"
echo "  XXL-JOB   ${SERVER_HOST}:19998 -> 本机 127.0.0.1:19999"
echo "  SkyWalking Agent 本机 127.0.0.1:11800 -> 服务器 127.0.0.1:11800"
echo "  SkyWalking UI    本机 127.0.0.1:18084 -> 服务器 127.0.0.1:18084"
echo "按 Ctrl+C 关闭隧道。"

exec ssh \
  -N -T \
  -i "${SSH_KEY}" \
  -o ExitOnForwardFailure=yes \
  -o ServerAliveInterval=60 \
  -o ServerAliveCountMax=3 \
  -L "127.0.0.1:11800:127.0.0.1:11800" \
  -L "127.0.0.1:18084:127.0.0.1:18084" \
  -R "0.0.0.0:8720:127.0.0.1:8720" \
  -R "0.0.0.0:19998:127.0.0.1:19999" \
  "${SERVER_USER}@${SERVER_HOST}"
