#!/usr/bin/env bash
# 下载官方番茄小说下载器安卓 arm64 引擎二进制到 vendor/，
# 并复制到 assets/（注意：不能放 jniLibs —— 安装器会把非共享库的裸 ELF 丢弃）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

REPO="zhongbai2333/Tomato-Novel-Downloader"
URL="https://github.com/${REPO}/releases/latest/download/TomatoNovelDownloader-Android_arm64"

VENDOR="$ROOT/vendor"
ASSETS="$ROOT/app/src/main/assets"
mkdir -p "$VENDOR" "$ASSETS"

echo "下载引擎：$URL"
curl -sL -o "$VENDOR/TomatoNovelDownloader-Android_arm64" "$URL"

echo "复制到 assets/tomato_engine"
cp "$VENDOR/TomatoNovelDownloader-Android_arm64" "$ASSETS/tomato_engine"

echo "完成："
ls -la "$ASSETS/tomato_engine"
file "$ASSETS/tomato_engine"
