#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .runtime
if [[ -e .runtime/processes.tsv ]]; then echo 'Run scripts/stop-local.sh first.'; exit 1; fi
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
for service in naming-server users-service bank-account crypto-wallet currency-exchange crypto-exchange currency-conversion trade-service api-gateway; do
  jar="$PWD/$service/target/$service-1.0.0.jar"
  [[ -f "$jar" ]] || { echo "Missing $jar; run mvn clean package"; exit 1; }
  nohup "$java_bin" -Xms48m -Xmx256m -jar "$jar" >".runtime/$service.log" 2>&1 &
  printf '%s\t%s\t%s\n' "$!" "$service" "$jar" >>.runtime/processes.tsv
  if [[ "$service" == naming-server ]]; then sleep 8; fi
done
echo 'Allow up to 90 seconds for discovery and provisioning. Gateway: http://localhost:8765'
