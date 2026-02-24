#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

DB_TYPE="${DB_TYPE:-h2}"
JDBC_JAR="${JDBC_JAR:-}"
KEEP_RUNNING="${KEEP_RUNNING:-0}"

export JAVATITAN_JWT_SECRET="${JAVATITAN_JWT_SECRET:-super-secret}"

if [[ "$DB_TYPE" != "h2" && "$DB_TYPE" != "postgres" ]]; then
  echo "DB_TYPE invalido: $DB_TYPE (use h2 ou postgres)" >&2
  exit 1
fi

if [[ "$DB_TYPE" == "h2" ]]; then
  export JAVATITAN_DB_URL="${JAVATITAN_DB_URL:-jdbc:h2:./data/javatitan}"
  export JAVATITAN_DB_DRIVER="${JAVATITAN_DB_DRIVER:-org.h2.Driver}"
  JDBC_JAR="${JDBC_JAR:-lib/h2.jar}"
  if [[ ! -f "$JDBC_JAR" ]]; then
    echo "Driver H2 nao encontrado em $JDBC_JAR" >&2
    echo "Baixe com:" >&2
    echo "  mkdir -p lib" >&2
    echo "  curl -L -o lib/h2.jar https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar" >&2
    exit 1
  fi
else
  export JAVATITAN_DB_URL="${JAVATITAN_DB_URL:-jdbc:postgresql://localhost:5432/javatitan}"
  export JAVATITAN_DB_USER="${JAVATITAN_DB_USER:-javatitan}"
  export JAVATITAN_DB_PASS="${JAVATITAN_DB_PASS:-javatitan}"
  export JAVATITAN_DB_DRIVER="${JAVATITAN_DB_DRIVER:-org.postgresql.Driver}"
  JDBC_JAR="${JDBC_JAR:-lib/postgresql.jar}"
  if [[ ! -f "$JDBC_JAR" ]]; then
    echo "Driver PostgreSQL nao encontrado em $JDBC_JAR" >&2
    echo "Baixe com:" >&2
    echo "  mkdir -p lib" >&2
    echo "  curl -L -o lib/postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar" >&2
    exit 1
  fi
fi

mkdir -p out
javac -cp "$JDBC_JAR" -d out $(find src/main/java -name "*.java")

java -cp "out:$JDBC_JAR" com.javatitan.engine.MotorFinanceiro &
SERVER_PID=$!

cleanup() {
  if [[ "$KEEP_RUNNING" != "1" ]]; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if command -v curl >/dev/null 2>&1; then
  for _ in {1..20}; do
    if curl -fsS "http://localhost:8080/health" >/dev/null; then
      break
    fi
    sleep 0.3
  done

  TOKEN=$(python3 - <<'PY'
import base64, json, hmac, hashlib, time, os
secret = os.getenv("JAVATITAN_JWT_SECRET", "super-secret")
header = {"alg":"HS256","typ":"JWT"}
payload = {
    "user":"demo",
    "plan":"PRO",
    "exp": int(time.time()) + 3600
}

def b64url(data):
    raw = json.dumps(data, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

h = b64url(header)
p = b64url(payload)
msg = f"{h}.{p}".encode()
sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), msg, hashlib.sha256).digest()).decode().rstrip("=")
print(f"{h}.{p}.{sig}")
PY
  )

  echo "TOKEN=${TOKEN}"
  echo "---- /health ----"
  curl -i "http://localhost:8080/health"
  echo
  echo "---- /api/calcular ----"
  curl -i -X POST "http://localhost:8080/api/calcular" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d '{"idCliente":"e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1","valorBruto":1000.00,"plano":"PRO"}'
else
  echo "curl nao encontrado. Servidor iniciado em http://localhost:8080" >&2
fi

if [[ "$KEEP_RUNNING" == "1" ]]; then
  echo "Servidor em execucao. Ctrl+C para encerrar." >&2
  wait "$SERVER_PID"
fi
