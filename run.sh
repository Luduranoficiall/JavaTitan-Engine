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

sleep 0.6

TOKEN=$(java -cp out com.javatitan.engine.TokenGenerator)
export JAVATITAN_JWT_TOKEN="$TOKEN"
export JAVATITAN_BASE_URL="http://localhost:${PORT}"

echo "TOKEN=${TOKEN}"
java -cp out com.javatitan.engine.TestClient
