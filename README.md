# JavaTitan Engine

Motor financeiro em Java, orientado a API HTTP, com processamento assíncrono, validação de acesso por JWT (simplificada), regras de negócio por plano e observabilidade via logs estruturados. O foco é clareza arquitetural, precisão com `BigDecimal` e uso de padrões modernos (records, streams, strategy e async com `CompletableFuture`).

## Destaques tecnicos
- Pipeline HTTP enxuto com `HttpServer` nativo e handlers dedicados.
- Processamento assíncrono com `CompletableFuture` e pool dedicado.
- Validação de acesso baseada em claim `plan` no JWT (Base64URL).
- Domínio forte via `Plano` (enum com taxas e validação centralizada).
- Parsing manual de JSON com utilitário próprio (`JsonUtils`) para manter dependências zero.

## Arquitetura (alto nivel)

```
Cliente HTTP
  |  POST /api/calcular  (Bearer JWT)
  v
HttpServer
  |-- CalculoHandler
      |-- valida Authorization
      |-- parse JSON (JsonUtils)
      |-- valida plano (ValidadorSeguranca)
      |-- MotorFinanceiroEspecialista (async)
      |-- OrcamentoDAO (memoria)
      v
   JSON response
```

### Fluxo de processamento (sequencia simplificada)

```
Request -> Handler -> Validacao -> Async Calc -> Persistencia -> Response
```

## Modelo de dominio
- `Plano`: enum com taxa (`STARTER`, `PRO`, `VIP`).
- `PropostaRequest`: idCliente, valorBruto, plano.
- `PropostaResponse`: idProposta, valorLiquido, taxaAplicada, status.

## API HTTP
### `POST /api/calcular`
Calcula proposta financeira.

**Headers**
- `Authorization: Bearer <token>` (obrigatorio)
- `Content-Type: application/json`

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

**Erros comuns**
- `401`: Authorization ausente ou token vazio.
- `403`: token invalido ou plano insuficiente.
- `400`: JSON invalido ou plano desconhecido.
- `500`: falha interna.

### `GET /health`
Health check.

**Resposta 200**
```json
{ "status": "UP" }
```

## Seguranca (JWT simplificado)
- Espera `Bearer header.payload.assinatura`.
- O payload e decodificado via Base64URL (com padding corrigido quando necessario).
- A claim esperada e `"plan":"<PLANO>"`.
- A assinatura **nao** e validada neste projeto (demo).

**Exemplo de token para plano PRO**
```
Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiZGVtbyIsInBsYW4iOiJQUk8ifQ==.assinatura_fake
```

## Regras de negocio
- Taxas centralizadas em `Plano`.
- `MotorFinanceiroEspecialista` calcula `valorLiquido = valorBruto - (valorBruto * taxa)`.
- `MotorRegrasElite` demonstra Strategy Pattern com as mesmas taxas.

## Concorrencia e performance
- `HttpServer` usa pool dedicado para requests.
- O calculo roda em pool separado (evita bloquear threads de entrada).
- `OrcamentoDAO` usa `CopyOnWriteArrayList` para segurança de acesso concorrente.

## Execucao local
**Requisitos**
- JDK 17+ (records e switch/modern features).

**Compilar**
```bash
cd /home/luduranoficiall/JavaTitan-Engine
javac *.java
```

**Subir a API**
```bash
java MotorFinanceiro
```

**Testar**
```bash
curl -i http://localhost:8080/health
```

```bash
curl -i -X POST http://localhost:8080/api/calcular \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiZGVtbyIsInBsYW4iOiJQUk8ifQ==.assinatura_fake' \
  -d '{"idCliente":"e7f6b1c6-9cb0-4c1a-9c76-2a9bf3b2a1c1","valorBruto":1000.00,"plano":"PRO"}'
```

## Demonstracoes
**Strategy Pattern (Regras)**
```bash
java MotorRegrasElite
```

**Processamento em lote**
```bash
java ProcessadorLote
```

**Logger estruturado**
```bash
java LoggerSaaS
```

## Observabilidade
- Logs estruturados com timestamp e nivel.
- Eventos de seguranca e pipeline assincrono logados.

## Estrutura do projeto
```
JavaTitan-Engine/
  .gitignore
  JsonUtils.java
  LoggerSaaS.java
  MotorFinanceiro.java
  MotorRegrasElite.java
  Plano.java
  ProcessadorLote.java
  ValidadorSeguranca.java
```

## Limitações conscientes
- Parsing manual de JSON (nao cobre JSON complexo).
- Sem validacao de assinatura JWT.
- Persistencia apenas em memoria.
- Sem TLS, rate limiting ou observabilidade com metrics/tracing.

## Roadmap sugerido
- Adicionar Jackson/Gson para parsing.
- Validar JWT com assinatura e exp.
- DAO com banco real (PostgreSQL/H2).
- Metrics (Micrometer) e tracing (OpenTelemetry).
- Testes unitarios e integracao (JUnit + Testcontainers).
