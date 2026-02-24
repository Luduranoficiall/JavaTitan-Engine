# JavaTitan Engine (TCC)

Motor financeiro em **Java puro**, com API HTTP, processamento assincrono, validacao JWT HS256 e persistencia JDBC opcional. O projeto foi estruturado para TCC, com foco em arquitetura limpa, rastreabilidade e demonstracao tecnica.

## Destaques tecnicos
- API HTTP nativa (`HttpServer`) com handlers isolados.
- Processamento assincrono com pool dedicado (nao bloqueia threads de entrada).
- JWT HS256 com validacao de assinatura, `exp` e claims opcionais (`iss`/`aud`).
- Dominio forte via `Plano` (enum com taxas e validacao centralizada).
- Persistencia JDBC configuravel por variaveis de ambiente.
- Respostas padronizadas de erro com `requestId` e timestamp.
- Execucao e testes 100% em Java (sem scripts shell).

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
  "idProposta": "5c8f0a80-1e5d-4b9a-a1f9-83e0dd2e8a1c1",
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
- `JAVATITAN_JWT_TTL` (segundos, default: `3600`)
- `JAVATITAN_JWT_PLAN` (default: `PRO`)

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

## Execucao (Java puro)
Compilar:
```bash
javac -d out $(find src/main/java -name "*.java")
```

### Subir o servidor (padrao)
```bash
export JAVATITAN_JWT_SECRET="super-secret"
java -cp out com.javatitan.engine.MotorFinanceiro
```

### Gerar token (Java puro)
```bash
java -cp out com.javatitan.engine.TokenGenerator
```

### Testar endpoints (Java puro)
```bash
export JAVATITAN_JWT_TOKEN="<TOKEN_GERADO>"
java -cp out com.javatitan.engine.TestClient
```

### Runner TCC (start + test)
```bash
export JAVATITAN_JWT_SECRET="super-secret"
java -cp out com.javatitan.engine.TccRunner
```

Com DB (JDBC):
```bash
export JAVATITAN_DB_URL="jdbc:h2:./data/javatitan"
export JAVATITAN_DB_DRIVER="org.h2.Driver"
java -cp out:lib/h2.jar com.javatitan.engine.TccRunner
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
    TestClient.java
    TokenGenerator.java
    TccRunner.java
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
