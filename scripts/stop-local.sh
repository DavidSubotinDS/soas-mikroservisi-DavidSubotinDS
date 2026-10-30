#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .runtime/processes.tsv ]] || exit 0
while IFS=$'\t' read -r process_id service jar; do
  if ps -p "$process_id" -o args= | grep -Fq -- "$jar"; then kill "$process_id"; echo "Stopped $service"; fi
done <.runtime/processes.tsv
rm .runtime/processes.tsv
