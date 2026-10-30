#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
node scripts/smoke-test.mjs "--base=${1:-http://localhost:8765}"
