#!/usr/bin/env bash
set -e

TARGET_URL=${1:-"https://ticket-booking-service-production-27ac.up.railway.app"}

echo "=========================================================="
echo " Running Burst Load Test against: $TARGET_URL"
echo "=========================================================="

if command -v python3 &>/dev/null; then
    python3 -m pip install -q aiohttp 2>/dev/null || true
    python3 scripts/burst.py --base-url "$TARGET_URL"
elif command -v python &>/dev/null; then
    python -m pip install -q aiohttp 2>/dev/null || true
    python scripts/burst.py --base-url "$TARGET_URL"
elif command -v java &>/dev/null; then
    java scripts/BurstTest.java "$TARGET_URL"
else
    echo "Error: Neither python nor java found to run the burst test."
    exit 1
fi
