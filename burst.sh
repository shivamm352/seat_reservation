#!/usr/bin/env bash
set -e

TARGET_URL=${1:-"http://localhost:8080"}

echo "=========================================================="
echo " Running Burst Load Test against: $TARGET_URL"
echo "=========================================================="

# Ensure aiohttp dependency is available
python3 -m pip install -q aiohttp 2>/dev/null || pip install -q aiohttp

# Execute asynchronous burst engine
python3 scripts/burst.py --base-url "$TARGET_URL"
