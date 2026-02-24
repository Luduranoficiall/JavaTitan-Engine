package com.javatitan.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

record PropostaRequest(UUID idCliente, BigDecimal valorBruto, Plano plano) {}

record PropostaResponse(UUID idProposta, BigDecimal valorLiquido, BigDecimal taxaAplicada, String status) {}

class MotorFinanceiroEspecialista {
    private final ExecutorService executor;
    private final long delayMs;

    MotorFinanceiroEspecialista(ExecutorService executor, long delayMs) {
        this.executor = executor;
        this.delayMs = delayMs;
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
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processamento interrompido.", e);
        }
    }
}

public class MotorFinanceiro {
    private static final String CONTEXT_CALCULO = "/api/calcular";
    private static final String CONTEXT_CALCULO_SECURE = "/api/calcular-secure";
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

        CryptoConfig cryptoConfig;
        try {
            cryptoConfig = CryptoConfig.fromEnv();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            LoggerSaaS.log("ERROR", "[CRYPTO] " + ex.getMessage());
            return;
        }

        TlsConfig tlsConfig;
        try {
            tlsConfig = TlsConfig.fromEnv();
        } catch (IllegalStateException ex) {
            LoggerSaaS.log("ERROR", "[TLS] " + ex.getMessage());
            return;
        }

        if (cryptoConfig.secureMode() && !tlsConfig.enabled()) {
            LoggerSaaS.log("ERROR", "[TLS] Secure mode exige TLS habilitado.");
            return;
        }

        DbConfig dbConfig = DbConfig.fromEnv();
        ServerHandle handle;
        try {
            handle = startServer(appConfig, jwtConfig, dbConfig, cryptoConfig, tlsConfig);
        } catch (IllegalStateException ex) {
            LoggerSaaS.log("ERROR", "[DB] " + ex.getMessage());
            return;
        }

        LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO] Servidor iniciado na porta " + appConfig.port() + ".");

        Runtime.getRuntime().addShutdownHook(new Thread(handle::close));
    }

    public static ServerHandle startServer(AppConfig appConfig, JwtConfig jwtConfig, DbConfig dbConfig, CryptoConfig cryptoConfig, TlsConfig tlsConfig) throws IOException {
        OrcamentoRepository repository = criarRepositorio(dbConfig);
        ExecutorService httpExecutor = Executors.newFixedThreadPool(appConfig.httpThreads());
        ExecutorService workerExecutor = Executors.newFixedThreadPool(appConfig.workerThreads());

        MotorFinanceiroEspecialista especialista = new MotorFinanceiroEspecialista(workerExecutor, appConfig.simulatedDelayMs());
        MetricsRegistry metricsRegistry = new MetricsRegistry();
        RequestLimiter requestLimiter = new RequestLimiter(appConfig.rateLimitPerMinute(), 60000L);

        HttpServer server = createServer(appConfig, tlsConfig);
        server.createContext(CONTEXT_CALCULO, new CalculoHandler(especialista, repository, jwtConfig, cryptoConfig, false, appConfig, requestLimiter, metricsRegistry));
        server.createContext(CONTEXT_CALCULO_SECURE, new CalculoHandler(especialista, repository, jwtConfig, cryptoConfig, true, appConfig, requestLimiter, metricsRegistry));
        server.createContext(CONTEXT_HEALTH, new HealthCheckHandler());
        if (appConfig.metricsEnabled()) {
            server.createContext("/metrics", new MetricsHandler(metricsRegistry, cryptoConfig.secureMode()));
        }
        server.setExecutor(httpExecutor);
        server.start();

        return new ServerHandle(server, httpExecutor, workerExecutor, repository);
    }

    private static HttpServer createServer(AppConfig appConfig, TlsConfig tlsConfig) throws IOException {
        if (tlsConfig != null && tlsConfig.enabled()) {
            SSLContext sslContext = tlsConfig.createServerContext();
            HttpsServer server = HttpsServer.create(new InetSocketAddress(appConfig.port()), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(com.sun.net.httpserver.HttpsParameters params) {
                    params.setNeedClientAuth(tlsConfig.requireClientAuth());
                    params.setSSLParameters(sslContext.getDefaultSSLParameters());
                }
            });
            return server;
        }
        return HttpServer.create(new InetSocketAddress(appConfig.port()), 0);
    }

    private static OrcamentoRepository criarRepositorio(DbConfig config) {
        if (config == null || !config.isEnabled()) {
            LoggerSaaS.log("WARN", "[DB] JAVATITAN_DB_URL nao definido. Usando memoria.");
            return new InMemoryOrcamentoRepository();
        }
        return new JdbcOrcamentoRepository(config);
    }

    static class ServerHandle implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService httpExecutor;
        private final ExecutorService workerExecutor;
        private final OrcamentoRepository repository;

        ServerHandle(HttpServer server, ExecutorService httpExecutor, ExecutorService workerExecutor, OrcamentoRepository repository) {
            this.server = server;
            this.httpExecutor = httpExecutor;
            this.workerExecutor = workerExecutor;
            this.repository = repository;
        }

        @Override
        public void close() {
            LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO] Encerrando servidor...");
            server.stop(1);
            httpExecutor.shutdown();
            workerExecutor.shutdown();
            repository.close();
        }
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
        private final CryptoConfig cryptoConfig;
        private final boolean secureEndpoint;
        private final AppConfig appConfig;
        private final RequestLimiter requestLimiter;
        private final MetricsRegistry metricsRegistry;

        CalculoHandler(MotorFinanceiroEspecialista motor, OrcamentoRepository repository, JwtConfig jwtConfig, CryptoConfig cryptoConfig, boolean secureEndpoint, AppConfig appConfig, RequestLimiter requestLimiter, MetricsRegistry metricsRegistry) {
            this.motor = motor;
            this.repository = repository;
            this.jwtConfig = jwtConfig;
            this.cryptoConfig = cryptoConfig;
            this.secureEndpoint = secureEndpoint;
            this.appConfig = appConfig;
            this.requestLimiter = requestLimiter;
            this.metricsRegistry = metricsRegistry;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startedAt = System.nanoTime();
            String requestId = requestId(exchange);

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpResponses.sendJson(exchange, 405, HttpResponses.errorJson(405, "Metodo nao permitido", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }

            if (requestLimiter.enabled() && !requestLimiter.tryAcquire(remoteKey(exchange))) {
                HttpResponses.sendJson(exchange, 429, HttpResponses.errorJson(429, "Muitas requisicoes", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }

            if (cryptoConfig != null && cryptoConfig.secureMode() && !secureEndpoint && !appConfig.allowPlainWhenSecure()) {
                HttpResponses.sendJson(exchange, 403, HttpResponses.errorJson(403, "Use o endpoint seguro", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }

            if (secureEndpoint && (cryptoConfig == null || cryptoConfig.aesKey() == null)) {
                HttpResponses.sendJson(exchange, 400, HttpResponses.errorJson(400, "Criptografia nao configurada", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
                HttpResponses.sendJson(exchange, 415, HttpResponses.errorJson(415, "Content-Type deve ser application/json", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                LoggerSaaS.log("WARN", requestId, "Authorization ausente ou malformado.");
                HttpResponses.sendJson(exchange, 401, HttpResponses.errorJson(401, "Authorization Bearer obrigatorio", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }
            String token = authHeader.substring(7).trim();
            if (token.isEmpty()) {
                HttpResponses.sendJson(exchange, 401, HttpResponses.errorJson(401, "Token Bearer vazio", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
                return;
            }

            try {
                String jsonPayload = HttpRequestReader.readBodyLimited(exchange, appConfig.maxBodyBytes()).trim();
                if (jsonPayload.isEmpty()) {
                    HttpResponses.sendJson(exchange, 400, HttpResponses.errorJson(400, "Body vazio", requestId), requestId);
                    metricsRegistry.record(false, durationMs(startedAt));
                    return;
                }

                String effectivePayload = jsonPayload;
                if (secureEndpoint) {
                    CryptoUtils.EncryptedPayload encrypted = CryptoUtils.readPayload(jsonPayload);
                    effectivePayload = CryptoUtils.decrypt(encrypted, cryptoConfig.aesKey());
                }

                PropostaRequest request = parseRequest(effectivePayload);

                if (!ValidadorSeguranca.validarAcesso(token, request.plano(), jwtConfig)) {
                    HttpResponses.sendJson(exchange, 403, HttpResponses.errorJson(403, "Acesso negado", requestId), requestId);
                    metricsRegistry.record(false, durationMs(startedAt));
                    return;
                }

                CompletableFuture<PropostaResponse> future = motor.processarAsync(request, requestId);
                if (appConfig.processingTimeoutMs() > 0) {
                    future = future.orTimeout(appConfig.processingTimeoutMs(), TimeUnit.MILLISECONDS);
                }

                future.thenAccept(response -> {
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

                        String jsonResponse = jsonSucesso(response);
                        if (secureEndpoint) {
                            CryptoUtils.EncryptedPayload encryptedResponse = CryptoUtils.encrypt(jsonResponse, cryptoConfig.aesKey());
                            jsonResponse = CryptoUtils.writePayload(encryptedResponse);
                        }
                        HttpResponses.sendJson(exchange, 200, jsonResponse, requestId);
                        metricsRegistry.record(true, durationMs(startedAt));
                    } catch (RuntimeException ex) {
                        LoggerSaaS.log("ERROR", requestId, "Falha ao persistir: " + ex.getMessage());
                        try {
                            HttpResponses.sendJson(exchange, 500, HttpResponses.errorJson(500, "Falha ao persistir", requestId), requestId);
                        } catch (IOException e) {
                            LoggerSaaS.log("ERROR", requestId, "Falha ao enviar erro: " + e.getMessage());
                        }
                        metricsRegistry.record(false, durationMs(startedAt));
                    } catch (IOException ex) {
                        LoggerSaaS.log("ERROR", requestId, "Falha ao enviar resposta: " + ex.getMessage());
                        metricsRegistry.record(false, durationMs(startedAt));
                    }
                }).exceptionally(ex -> {
                    Throwable causa = (ex instanceof CompletionException && ex.getCause() != null)
                        ? ex.getCause()
                        : ex;
                    int status = 500;
                    if (causa instanceof TimeoutException) {
                        status = 504;
                    } else if (causa instanceof IllegalArgumentException) {
                        status = 400;
                    }
                    try {
                        HttpResponses.sendJson(exchange, status, HttpResponses.errorJson(status, causa.getMessage(), requestId), requestId);
                    } catch (IOException e) {
                        LoggerSaaS.log("ERROR", requestId, "Falha ao enviar erro: " + e.getMessage());
                    }
                    metricsRegistry.record(false, durationMs(startedAt));
                    return null;
                });
            } catch (RequestValidationException e) {
                HttpResponses.sendJson(exchange, e.status(), HttpResponses.errorJson(e.status(), e.getMessage(), requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
            } catch (IllegalArgumentException e) {
                HttpResponses.sendJson(exchange, 400, HttpResponses.errorJson(400, e.getMessage(), requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
            } catch (Exception e) {
                HttpResponses.sendJson(exchange, 500, HttpResponses.errorJson(500, "Falha interna", requestId), requestId);
                metricsRegistry.record(false, durationMs(startedAt));
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

    static class MetricsHandler implements HttpHandler {
        private final MetricsRegistry metricsRegistry;
        private final boolean secureMode;

        MetricsHandler(MetricsRegistry metricsRegistry, boolean secureMode) {
            this.metricsRegistry = metricsRegistry;
            this.secureMode = secureMode;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpResponses.sendJson(exchange, 405, HttpResponses.errorJson(405, "Metodo nao permitido", null), null);
                return;
            }
            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
                HttpResponses.sendJson(exchange, 403, HttpResponses.errorJson(403, "Acesso local apenas", null), null);
                return;
            }
            String response = metricsRegistry.toJson(secureMode);
            HttpResponses.sendJson(exchange, 200, response, null);
        }
    }

    private static String requestId(HttpExchange exchange) {
        String existing = exchange.getRequestHeaders().getFirst("X-Request-Id");
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return UUID.randomUUID().toString();
    }

    private static long durationMs(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static String remoteKey(HttpExchange exchange) {
        if (exchange == null || exchange.getRemoteAddress() == null) {
            return "unknown";
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }
}
