# JavaTitan Engine

Motor financeiro em Java com API HTTP minimalista, processamento assíncrono e validação de acesso baseada em JWT (simplificada). O projeto foca em arquitetura enxuta, precisão com `BigDecimal` e padrões modernos (records, streams, strategy, async com `CompletableFuture`).

## Visao geral
O JavaTitan Engine expõe um endpoint de calculo financeiro que recebe uma proposta, valida acesso por plano, processa de forma assíncrona e retorna o valor liquido com taxa aplicada. O sistema inclui logger estruturado, regras de negocio por plano e um DAO em memoria para demonstrar persistencia.

## Arquitetura (alto nivel)

```
Cliente HTTP
  |  POST /api/calcular  (Bearer JWT)
  v
HttpServer (Java nativo)
  |-- CalculoHandler
        |-- valida Authorization (Bearer)
        |-- parse JSON (regex)
        |-- valida plano no JWT
        |-- MotorFinanceiroEspecialista (async)
        |-- OrcamentoDAO (memoria)
        v
   Response JSON
```

## Componentes principais
- `MotorFinanceiro.java`: servidor HTTP, handlers, pipeline de validacao e processamento assincrono.
- `MotorFinanceiroEspecialista` (interno): calculo do valor liquido por plano, com simulacao de carga.
- `LoggerSaaS.java`: logger estruturado com timestamp e nivel.
- `MotorRegrasElite.java`: motor de regras com Strategy Pattern (Map de funcoes).
- `ProcessadorLote.java`: processamento em lote com Streams.
- `ValidadorSeguranca.java`: validador de JWT (standalone). O `MotorFinanceiro` possui sua propria versao interna para a API.

## API HTTP
### `POST /api/calcular`
Calcula proposta com base em plano e valor bruto.

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
- `401`: Authorization ausente ou malformado.
- `403`: token invalido ou plano insuficiente.
- `500`: erro de parsing ou falha interna.

### `GET /health`
Health check simples.

**Resposta 200**
```json
{ "status": "UP" }
```

## Seguranca (JWT simplificado)
- O token e esperado no formato `Bearer header.payload.assinatura`.
- O payload e decodificado via Base64 e deve conter a claim `"plan":"<PLANO>"`.
- O plano do token deve bater com o `plano` enviado na requisicao.

**Exemplo de token para plano PRO**
```
Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiZGVtbyIsInBsYW4iOiJQUk8ifQ==.assinatura_fake
```

## Regras de negocio (taxas)
No endpoint `/api/calcular`:
- `STARTER` = 6%
- `PRO` = 15%
- default = 0%

No `MotorRegrasElite` (demo de Strategy Pattern):
- `VIP` = 2%
- `STARTER` = 10%
- `PRO` = 5%

## Execucao local
**Requisitos**
- JDK 17+ (records e switch expressions).

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

## Modos de demonstracao
**Motor de Regras (Strategy Pattern)**
```bash
java MotorRegrasElite
```

**Processamento em Lote (Streams)**
```bash
java ProcessadorLote
```

**Logger**
```bash
java LoggerSaaS
```

## Observabilidade
- Logs estruturados via `LoggerSaaS` com timestamp e nivel.
- Logs de seguranca e de pipeline assincrono sao emitidos no console.

## Limitacoes atuais (conscientes)
- Parsing de JSON via regex (nao cobre JSON complexo).
- Validacao de JWT e simples (sem assinatura real ou exp).
- Persistencia em memoria (lista em `OrcamentoDAO`).
- Sem TLS/HTTPS e sem rate limiting.

## Roadmap sugerido
- Trocar parsing por Jackson/Gson.
- Implementar DAO com banco (PostgreSQL, H2 ou SQLite).
- Validar JWT com assinatura e expiracao.
- Observabilidade com metrics e tracing.
- Testes automatizados (unit e integration).

## Estrutura do projeto
```
JavaTitan-Engine/
  LoggerSaaS.java
  MotorFinanceiro.java
  MotorRegrasElite.java
  ProcessadorLote.java
  ValidadorSeguranca.java
```
