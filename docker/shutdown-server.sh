#!/usr/bin/env bash

# 手动关闭整台服务器。
set -euo pipefail

read -r -p "确定要关闭整台服务器吗？输入 YES 继续：" confirmation

if [[ "$confirmation" != "YES" ]]; then
  echo "已取消。"
  exit 0
fi

if [[ "$EUID" -eq 0 ]]; then
  shutdown -h now
else
  sudo shutdown -h now
fi
