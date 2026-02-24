# JavaTitan Engine

Motor financeiro em Java com API HTTP, processamento assincrono, validacao JWT HS256 e persistencia JDBC opcional. O projeto prioriza clareza arquitetural, precisao com `BigDecimal` e padroes modernos (records, streams, strategy e async com `CompletableFuture`).

## Destaques tecnicos
- API HTTP nativa (`HttpServer`) com handlers isolados.
- Processamento assincrono com pool dedicado (nao bloqueia threads de entrada).
- JWT HS256 com validacao de assinatura, `exp` e claims opcionais (`iss`/`aud`).
- Dominio forte via `Plano` (enum com taxas e validacao centralizada).
- Persistencia JDBC configuravel por variaveis de ambiente.
- Respostas padronizadas de erro com `requestId` e timestamp.

## Arquitetura (alto nivel)

```
Cliente HTTP
  |  POST /api/calcular  (Bearer JWT)
  v
HttpServer
  |-- CalculoHandler
      |-- valida Authorization
      |-- parse JSON (JsonUtils)
      |-- valida JWT (ValidadorSeguranca)
      |-- MotorFinanceiroEspecialista (async)
      |-- OrcamentoRepository (JDBC ou memoria)
      v
   JSON response
```

## Modelo de dominio
- `Plano`: `STARTER`, `PRO`, `VIP`.
- `PropostaRequest`: idCliente, valorBruto, plano.
- `PropostaResponse`: idProposta, valorLiquido, taxaAplicada, status.
- `Orcamento`: agregado persistido (request + response + timestamp).

## API HTTP
### `POST /api/calcular`
Calcula proposta financeira.

**Headers**
- `Authorization: Bearer <token>` (obrigatorio)
- `Content-Type: application/json`
- `X-Request-Id` (opcional; se ausente, o servidor gera)

**Body**
```json
{
  "idCliente": "e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1",
  "valorBruto": 1000.00,
  "plano": "PRO"
}
```

**Resposta 200**
```json
{
  "idProposta": "5c8f0a80-1e5d-4b9a-a1f9-83e0dd2e8a1e",
  "valorLiquido": 850.00,
  "taxaAplicada": 150.00,
  "status": "PROCESSADO_ASYNC"
}
```

**Resposta de erro (exemplo)**
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Acesso negado",
  "requestId": "b2a1a2e1-3e1d-4f9a-9e5d-2c3f1b9e4e99",
  "timestamp": "2025-02-23T21:10:00Z"
}
```

### `GET /health`
Health check.

**Resposta 200**
```json
{ "status": "UP", "timestamp": "2025-02-23T21:10:00Z" }
```

## Configuracao (APP)
- `JAVATITAN_PORT` (default: `8080`)
- `JAVATITAN_HTTP_THREADS` (default: `max(4, cpu)`)
- `JAVATITAN_WORKER_THREADS` (default: `max(2, cpu)`)

## JWT (HS256)
- Assinatura HS256 obrigatoria.
- `exp` obrigatorio por padrao (pode ser desligado).
- `iss` e `aud` opcionais.
- `clock skew` configuravel.

### Variaveis de ambiente (JWT)
- `JAVATITAN_JWT_SECRET` (obrigatorio)
- `JAVATITAN_JWT_REQUIRE_EXP` (default: `true`)
- `JAVATITAN_JWT_ISS` (opcional)
- `JAVATITAN_JWT_AUD` (opcional)
- `JAVATITAN_JWT_CLOCK_SKEW` (segundos, default: `30`)

### Gerar token valido (exemplo Python)
```bash
python3 - <<'PY'
import base64, json, hmac, hashlib, time
secret = "super-secret"
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
```

## Persistencia JDBC
A persistencia e habilitada quando `JAVATITAN_DB_URL` esta definida. Caso contrario, o motor usa memoria.

### Variaveis de ambiente (DB)
- `JAVATITAN_DB_URL` (ex: `jdbc:h2:./data/javatitan`)
- `JAVATITAN_DB_USER` (opcional)
- `JAVATITAN_DB_PASS` (opcional)
- `JAVATITAN_DB_DRIVER` (opcional, ex: `org.h2.Driver`)

### Schema criado automaticamente
```
orcamentos (
  id_proposta VARCHAR(36) PRIMARY KEY,
  id_cliente VARCHAR(36) NOT NULL,
  plano VARCHAR(16) NOT NULL,
  valor_bruto DECIMAL(19,2) NOT NULL,
  taxa_aplicada DECIMAL(19,2) NOT NULL,
  valor_liquido DECIMAL(19,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  criado_em TIMESTAMP NOT NULL
)
```

### Exemplo com H2 (local)
1) Baixe o driver (exemplo):
```bash
mkdir -p lib
curl -L -o lib/h2.jar https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar
```

2) Compile e execute:
```bash
export JAVATITAN_DB_URL="jdbc:h2:./data/javatitan"
export JAVATITAN_DB_DRIVER="org.h2.Driver"
export JAVATITAN_JWT_SECRET="super-secret"

javac -cp lib/h2.jar -d out $(find src/main/java -name "*.java")
java -cp out:lib/h2.jar com.javatitan.engine.MotorFinanceiro
```

## Build e execucao (Maven)
```bash
mvn -q -DskipTests package
export JAVATITAN_JWT_SECRET="super-secret"
java -jar target/javatitan-engine-1.0.0.jar
```

## Build e execucao (javac)
```bash
export JAVATITAN_JWT_SECRET="super-secret"

javac -d out $(find src/main/java -name "*.java")
java -cp out com.javatitan.engine.MotorFinanceiro
```

## Script rapido (build + run + teste)
```bash
./run.sh
```

## Testes rapidos
```bash
curl -i http://localhost:8080/health
```

```bash
curl -i -X POST http://localhost:8080/api/calcular \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <TOKEN_GERADO>' \
  -d '{"idCliente":"e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1","valorBruto":1000.00,"plano":"PRO"}'
```

## Demonstracoes
**Strategy Pattern (Regras)**
```bash
java -cp out com.javatitan.engine.MotorRegrasElite
```

**Processamento em lote**
```bash
java -cp out com.javatitan.engine.ProcessadorLote
```

## Estrutura do projeto
```
JavaTitan-Engine/
  pom.xml
  README.md
  .gitignore
  src/main/java/com/javatitan/engine/
    AppConfig.java
    DbConfig.java
    HttpResponses.java
    InMemoryOrcamentoRepository.java
    JdbcOrcamentoRepository.java
    JsonUtils.java
    JwtConfig.java
    LoggerSaaS.java
    MotorFinanceiro.java
    MotorRegrasElite.java
    Orcamento.java
    OrcamentoRepository.java
    Plano.java
    ProcessadorLote.java
    ValidadorSeguranca.java
```

## Limitacoes conscientes
- Parsing manual de JSON.
- Valida `aud` como string simples.
- Persistencia depende de driver JDBC externo.

## Roadmap sugerido
- JSON parsing com Jackson/Gson.
- JWT com JWKs e rotacao de chaves.
- Pool de conexoes JDBC.
- Observabilidade com metrics/tracing.
- Testes automatizados (JUnit + Testcontainers).
