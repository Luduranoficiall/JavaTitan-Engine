# TCC Checklist (JavaTitan Engine)

## Produto
- Objetivo do sistema e publico-alvo definidos.
- Fluxo principal do calculo documentado.
- API documentada com endpoints e exemplos.

## Seguranca
- TLS ativo com mTLS (keystore/truststore).
- Payload criptografado com AES-GCM no endpoint seguro.
- JWT HS256 com expiracao e plano.
- Limites de requisicao e payload configurados.

## Qualidade e evidencias
- Smoke test executado e anexado (JSON/CSV/TXT).
- Relatorio final gerado com TccReportGenerator.
- Logs de execucao e comprovantes anexados.
- Metricas locais validadas (endpoint /metrics).

## Operacao
- .env.tcc versionado localmente (nao commitar).
- Rotina de execucao com OneClickRunner.
- Portas e variaveis de ambiente registradas.
