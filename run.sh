#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

export JAVATITAN_JWT_SECRET="${JAVATITAN_JWT_SECRET:-super-secret}"
PORT="${JAVATITAN_PORT:-8080}"

mkdir -p out
javac -d out $(find src/main/java -name "*.java")

java -cp out com.javatitan.engine.MotorFinanceiro &
SERVER_PID=$!

cleanup() {
  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT

for _ in {1..20}; do
  if curl -fsS "http://localhost:${PORT}/health" >/dev/null; then
    break
  fi
  sleep 0.3
done

TOKEN=$(java -cp out com.javatitan.engine.TokenGenerator)

echo "TOKEN=${TOKEN}"

echo "---- /health ----"
curl -i "http://localhost:${PORT}/health"

echo

echo "---- /api/calcular ----"
curl -i -X POST "http://localhost:${PORT}/api/calcular" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"idCliente":"e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1","valorBruto":1000.00,"plano":"PRO"}'
