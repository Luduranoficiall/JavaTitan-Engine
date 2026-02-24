# JavaTitan Engine (TCC)

Motor financeiro em **Java puro**, com API HTTP, processamento assincrono, validacao JWT HS256, criptografia ponta a ponta e persistencia JDBC opcional. O projeto foi estruturado para TCC, com foco em arquitetura limpa, rastreabilidade e demonstracao tecnica.

## Destaques tecnicos
- API HTTP nativa (`HttpServer`) com handlers isolados.
- Processamento assincrono com pool dedicado (nao bloqueia threads de entrada).
- JWT HS256 com validacao de assinatura, `exp` e claims opcionais (`iss`/`aud`).
- Criptografia ponta a ponta: TLS + mTLS + payload AES-GCM.
- Dominio forte via `Plano` (enum com taxas e validacao centralizada).
- Persistencia JDBC configuravel por variaveis de ambiente.
- Respostas padronizadas de erro com `requestId` e timestamp.
- Execucao e testes 100% em Java (sem scripts shell).

## Arquitetura (alto nivel)

```
Cliente HTTP (mTLS)
  |  POST /api/calcular-secure (Bearer JWT + AES)
  v
HTTPS Server (TLS)
  |-- CalculoHandler
      |-- valida Authorization
      |-- descriptografa payload AES-GCM
      |-- valida JWT (ValidadorSeguranca)
      |-- MotorFinanceiroEspecialista (async)
      |-- OrcamentoRepository (JDBC ou memoria)
      |-- criptografa resposta AES-GCM
      v
   JSON criptografado
```

## Modelo de dominio
- `Plano`: `STARTER`, `PRO`, `VIP`.
- `PropostaRequest`: idCliente, valorBruto, plano.
- `PropostaResponse`: idProposta, valorLiquido, taxaAplicada, status.
- `Orcamento`: agregado persistido (request + response + timestamp).

## API HTTP
### `POST /api/calcular`
Endpoint padrao. **Em modo seguro** retorna erro, a menos que `JAVATITAN_ALLOW_PLAIN=true`.

### `POST /api/calcular-secure`
Endpoint com payload criptografado (AES-GCM).

**Body**
```json
{ "iv": "<base64>", "data": "<base64>" }
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
- `JAVATITAN_SIMULATED_DELAY_MS` (default: `0`)
- `JAVATITAN_ALLOW_PLAIN` (default: `false`)

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

## Criptografia ponta a ponta
### 1) TLS/HTTPS
- `JAVATITAN_TLS_KEYSTORE_PATH` (obrigatorio)
- `JAVATITAN_TLS_KEYSTORE_PASSWORD` (obrigatorio)
- `JAVATITAN_TLS_KEYSTORE_TYPE` (default: `JKS`)
- `JAVATITAN_TLS_KEY_PASSWORD` (opcional)

### 2) mTLS (cliente)
- `JAVATITAN_TLS_REQUIRE_CLIENT_AUTH` (default: `false`)
- `JAVATITAN_TLS_TRUSTSTORE_PATH` (obrigatorio se client auth estiver ativo)
- `JAVATITAN_TLS_TRUSTSTORE_PASSWORD`
- `JAVATITAN_TLS_TRUSTSTORE_TYPE` (default: `JKS`)

Cliente (para testes Java):
- `JAVATITAN_CLIENT_KEYSTORE_PATH`
- `JAVATITAN_CLIENT_KEYSTORE_PASSWORD`
- `JAVATITAN_CLIENT_KEYSTORE_TYPE`
- `JAVATITAN_CLIENT_KEY_PASSWORD`
- `JAVATITAN_CLIENT_TRUSTSTORE_PATH`
- `JAVATITAN_CLIENT_TRUSTSTORE_PASSWORD`
- `JAVATITAN_CLIENT_TRUSTSTORE_TYPE`

### 3) Payload AES-GCM
- `JAVATITAN_SECURE_MODE` (default: `false`)
- `JAVATITAN_AES_KEY` (base64, 16/24/32 bytes)

Gerar chave AES (Java puro):
```bash
javac -d out $(find src/main/java -name "*.java")
java -cp out com.javatitan.engine.AesKeyGenerator
```

Gerar keystore/truststore (Java puro, sem keytool manual):
```bash
javac -d out $(find src/main/java -name "*.java")
java -cp out com.javatitan.engine.KeystoreGenerator --mtls
```

Observacao: o gerador usa o binario `keytool` da JVM via Java (sem shell manual).

## Persistencia JDBC
A persistencia e habilitada quando `JAVATITAN_DB_URL` esta definida. Caso contrario, o motor usa memoria.

### Variaveis de ambiente (DB)
- `JAVATITAN_DB_URL` (ex: `jdbc:h2:./data/javatitan`)
- `JAVATITAN_DB_USER` (opcional)
- `JAVATITAN_DB_PASS` (opcional)
- `JAVATITAN_DB_DRIVER` (opcional, ex: `org.h2.Driver`)

## Execucao (Java puro)
Compilar:
```bash
javac -d out $(find src/main/java -name "*.java")
```

### Subir o servidor (TLS + AES)
```bash
export JAVATITAN_TLS_KEYSTORE_PATH="/caminho/keystore.jks"
export JAVATITAN_TLS_KEYSTORE_PASSWORD="senha"
export JAVATITAN_SECURE_MODE=true
export JAVATITAN_AES_KEY="<BASE64>"
export JAVATITAN_JWT_SECRET="super-secret"

java -cp out com.javatitan.engine.MotorFinanceiro
```

### Runner TCC (start + test)
```bash
export JAVATITAN_TLS_KEYSTORE_PATH="/caminho/keystore.jks"
export JAVATITAN_TLS_KEYSTORE_PASSWORD="senha"
export JAVATITAN_SECURE_MODE=true
export JAVATITAN_AES_KEY="<BASE64>"
export JAVATITAN_JWT_SECRET="super-secret"

java -cp out com.javatitan.engine.TccRunner --smoke-test
```

### Relatorio JSON do Smoke Test
```bash
export JAVATITAN_SMOKE_REPORT_PATH="reports/tcc-smoke.json"
java -cp out com.javatitan.engine.TccRunner --smoke-test
```

### TccReportGenerator (JSON + CSV)
```bash
java -cp out com.javatitan.engine.TccReportGenerator --out-dir=reports --name=tcc-final
```

### OneClickRunner (AES + keystore + servidor + smoke test)
```bash
java -cp out com.javatitan.engine.OneClickRunner
```

## Estrutura do projeto
```
JavaTitan-Engine/
  README.md
  .gitignore
  src/main/java/com/javatitan/engine/
    AesKeyGenerator.java
    AppConfig.java
    ClientTlsConfig.java
    CryptoConfig.java
    CryptoUtils.java
    DbConfig.java
    HttpClientFactory.java
    HttpResponses.java
    InMemoryOrcamentoRepository.java
    JdbcOrcamentoRepository.java
    JsonUtils.java
    JwtConfig.java
    KeystoreGenerator.java
    LoggerSaaS.java
    MotorFinanceiro.java
    MotorRegrasElite.java
    OneClickRunner.java
    Orcamento.java
    OrcamentoRepository.java
    Plano.java
    ProcessadorLote.java
    TestClient.java
    TccReportGenerator.java
    TccRunner.java
    TccSmokeTest.java
    TlsConfig.java
    TokenGenerator.java
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
