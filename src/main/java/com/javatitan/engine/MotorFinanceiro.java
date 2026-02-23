package com.javatitan.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

record PropostaRequest(UUID idCliente, BigDecimal valorBruto, Plano plano) {}

record PropostaResponse(UUID idProposta, BigDecimal valorLiquido, BigDecimal taxaAplicada, String status) {}

class MotorFinanceiroEspecialista {
    private final ExecutorService executor;

    MotorFinanceiroEspecialista(ExecutorService executor) {
        this.executor = executor;
    }

    public CompletableFuture<PropostaResponse> processarAsync(PropostaRequest request, String requestId) {
        return CompletableFuture.supplyAsync(() -> {
            LoggerSaaS.log("INFO", requestId, "Iniciando calculo para cliente: " + request.idCliente());
            simularCarga();

            if (request.valorBruto().signum() < 0) {
                throw new IllegalArgumentException("valorBruto nao pode ser negativo.");
            }

            BigDecimal taxa = request.plano().taxa();
            BigDecimal valorTaxa = request.valorBruto().multiply(taxa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal valorLiquido = request.valorBruto().subtract(valorTaxa);

            return new PropostaResponse(UUID.randomUUID(), valorLiquido, valorTaxa, "PROCESSADO_ASYNC");
        }, executor);
    }

    private void simularCarga() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processamento interrompido.", e);
        }
    }
}

public class MotorFinanceiro {
    private static final String CONTEXT_CALCULO = "/api/calcular";
    private static final String CONTEXT_HEALTH = "/health";

    public static void main(String[] args) throws IOException {
        AppConfig appConfig;
        try {
            appConfig = AppConfig.fromEnv();
        } catch (IllegalArgumentException ex) {
            LoggerSaaS.log("ERROR", "[CONFIG] " + ex.getMessage());
            return;
        }

        JwtConfig jwtConfig;
        try {
            jwtConfig = JwtConfig.fromEnv();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            LoggerSaaS.log("ERROR", "[CONFIG] " + ex.getMessage());
            return;
        }

        DbConfig dbConfig = DbConfig.fromEnv();
        OrcamentoRepository repository;
        try {
            repository = criarRepositorio(dbConfig);
        } catch (IllegalStateException ex) {
            LoggerSaaS.log("ERROR", "[DB] " + ex.getMessage());
            return;
        }

        ExecutorService httpExecutor = Executors.newFixedThreadPool(appConfig.httpThreads());
        ExecutorService workerExecutor = Executors.newFixedThreadPool(appConfig.workerThreads());

        HttpServer server = HttpServer.create(new InetSocketAddress(appConfig.port()), 0);
        server.createContext(CONTEXT_CALCULO, new CalculoHandler(
            new MotorFinanceiroEspecialista(workerExecutor),
            repository,
            jwtConfig
        ));
        server.createContext(CONTEXT_HEALTH, new HealthCheckHandler());
        server.setExecutor(httpExecutor);
        server.start();

        LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO] Servidor iniciado na porta " + appConfig.port() + ".");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO] Encerrando servidor...");
            server.stop(1);
            httpExecutor.shutdown();
            workerExecutor.shutdown();
            repository.close();
        }));
    }

    private static OrcamentoRepository criarRepositorio(DbConfig config) {
        if (config == null || !config.isEnabled()) {
            LoggerSaaS.log("WARN", "[DB] JAVATITAN_DB_URL nao definido. Usando memoria.");
            return new InMemoryOrcamentoRepository();
        }
        return new JdbcOrcamentoRepository(config);
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpResponses.sendJson(exchange, 405, HttpResponses.errorJson(405, "Metodo nao permitido", null), null);
                return;
            }
            String requestId = requestId(exchange);
            String response = "{\"status\":\"UP\",\"timestamp\":\"" + Instant.now().toString() + "\"}";
            HttpResponses.sendJson(exchange, 200, response, requestId);
        }
    }

    static class CalculoHandler implements HttpHandler {
        private final MotorFinanceiroEspecialista motor;
        private final OrcamentoRepository repository;
        private final JwtConfig jwtConfig;

        CalculoHandler(MotorFinanceiroEspecialista motor, OrcamentoRepository repository, JwtConfig jwtConfig) {
            this.motor = motor;
            this.repository = repository;
            this.jwtConfig = jwtConfig;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = requestId(exchange);

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpResponses.sendJson(exchange, 405, HttpResponses.errorJson(405, "Metodo nao permitido", requestId), requestId);
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                LoggerSaaS.log("WARN", requestId, "Authorization ausente ou malformado.");
                HttpResponses.sendJson(exchange, 401, HttpResponses.errorJson(401, "Authorization Bearer obrigatorio", requestId), requestId);
                return;
            }
            String token = authHeader.substring(7).trim();
            if (token.isEmpty()) {
                HttpResponses.sendJson(exchange, 401, HttpResponses.errorJson(401, "Token Bearer vazio", requestId), requestId);
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String jsonPayload = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (jsonPayload.isEmpty()) {
                    HttpResponses.sendJson(exchange, 400, HttpResponses.errorJson(400, "Body vazio", requestId), requestId);
                    return;
                }

                PropostaRequest request = parseRequest(jsonPayload);

                if (!ValidadorSeguranca.validarAcesso(token, request.plano(), jwtConfig)) {
                    HttpResponses.sendJson(exchange, 403, HttpResponses.errorJson(403, "Acesso negado", requestId), requestId);
                    return;
                }

                motor.processarAsync(request, requestId).thenAccept(response -> {
                    try {
                        Orcamento orcamento = new Orcamento(
                            response.idProposta(),
                            request.idCliente(),
                            request.plano(),
                            request.valorBruto(),
                            response.taxaAplicada(),
                            response.valorLiquido(),
                            response.status(),
                            Instant.now()
                        );
                        repository.salvar(orcamento);
                        HttpResponses.sendJson(exchange, 200, jsonSucesso(response), requestId);
                    } catch (RuntimeException ex) {
                        LoggerSaaS.log("ERROR", requestId, "Falha ao persistir: " + ex.getMessage());
                        try {
                            HttpResponses.sendJson(exchange, 500, HttpResponses.errorJson(500, "Falha ao persistir", requestId), requestId);
                        } catch (IOException e) {
                            LoggerSaaS.log("ERROR", requestId, "Falha ao enviar erro: " + e.getMessage());
                        }
                    } catch (IOException ex) {
                        LoggerSaaS.log("ERROR", requestId, "Falha ao enviar resposta: " + ex.getMessage());
                    }
                }).exceptionally(ex -> {
                    Throwable causa = (ex instanceof CompletionException && ex.getCause() != null)
                        ? ex.getCause()
                        : ex;
                    int status = (causa instanceof IllegalArgumentException) ? 400 : 500;
                    try {
                        HttpResponses.sendJson(exchange, status, HttpResponses.errorJson(status, causa.getMessage(), requestId), requestId);
                    } catch (IOException e) {
                        LoggerSaaS.log("ERROR", requestId, "Falha ao enviar erro: " + e.getMessage());
                    }
                    return null;
                });
            } catch (IllegalArgumentException e) {
                HttpResponses.sendJson(exchange, 400, HttpResponses.errorJson(400, e.getMessage(), requestId), requestId);
            } catch (Exception e) {
                HttpResponses.sendJson(exchange, 500, HttpResponses.errorJson(500, "Falha interna", requestId), requestId);
            }
        }

        private PropostaRequest parseRequest(String json) {
            UUID id = JsonUtils.readRequiredUuid(json, "idCliente");
            BigDecimal valor = JsonUtils.readRequiredBigDecimal(json, "valorBruto");
            String planoRaw = JsonUtils.readRequiredString(json, "plano");
            Plano plano = Plano.from(planoRaw);
            return new PropostaRequest(id, valor, plano);
        }

        private String jsonSucesso(PropostaResponse response) {
            return String.format(
                "{\"idProposta\":\"%s\",\"valorLiquido\":%.2f,\"taxaAplicada\":%.2f,\"status\":\"%s\"}",
                response.idProposta(), response.valorLiquido(), response.taxaAplicada(), response.status()
            );
        }
    }

    private static String requestId(HttpExchange exchange) {
        String existing = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return UUID.randomUUID().toString();
    }
}
