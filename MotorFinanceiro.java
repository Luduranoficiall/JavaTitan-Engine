import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

record PropostaRequest(UUID idCliente, BigDecimal valorBruto, Plano plano) {}

record PropostaResponse(UUID idProposta, BigDecimal valorLiquido, BigDecimal taxaAplicada, String status) {}

class MotorFinanceiroEspecialista {
    private final ExecutorService executor;

    MotorFinanceiroEspecialista(ExecutorService executor) {
        this.executor = executor;
    }

    public CompletableFuture<PropostaResponse> processarAsync(PropostaRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            LoggerSaaS.log("INFO", "[JAVA-THREAD] Iniciando calculo para cliente: " + request.idCliente());
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
    private static final int PORT = 8080;
    private static final String CONTEXT_CALCULO = "/api/calcular";
    private static final String CONTEXT_HEALTH = "/health";
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

    public static void main(String[] args) throws IOException {
        int httpThreads = Math.max(4, Runtime.getRuntime().availableProcessors());
        int workerThreads = Math.max(2, Runtime.getRuntime().availableProcessors());

        ExecutorService httpExecutor = Executors.newFixedThreadPool(httpThreads);
        ExecutorService workerExecutor = Executors.newFixedThreadPool(workerThreads);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext(CONTEXT_CALCULO, new CalculoHandler(
            new MotorFinanceiroEspecialista(workerExecutor),
            new OrcamentoDAO()
        ));
        server.createContext(CONTEXT_HEALTH, new HealthCheckHandler());
        server.setExecutor(httpExecutor);
        server.start();

        LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO] Servidor iniciado na porta " + PORT + ".");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO] Encerrando servidor...");
            server.stop(1);
            httpExecutor.shutdown();
            workerExecutor.shutdown();
        }));
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String response = "{\"status\":\"UP\"}";
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    static class CalculoHandler implements HttpHandler {
        private final MotorFinanceiroEspecialista motor;
        private final OrcamentoDAO dao;

        CalculoHandler(MotorFinanceiroEspecialista motor, OrcamentoDAO dao) {
            this.motor = motor;
            this.dao = dao;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarResposta(exchange, 405, jsonErro("Metodo nao permitido"));
                return;
            }

            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                LoggerSaaS.log("WARN", "Acesso negado: Header Authorization ausente ou malformado.");
                enviarResposta(exchange, 401, jsonErro("Header Authorization Bearer e obrigatorio"));
                return;
            }
            String token = authHeader.substring(7).trim();
            if (token.isEmpty()) {
                enviarResposta(exchange, 401, jsonErro("Token Bearer vazio"));
                return;
            }

            try (InputStream is = exchange.getRequestBody()) {
                String jsonPayload = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (jsonPayload.isEmpty()) {
                    enviarResposta(exchange, 400, jsonErro("Body vazio"));
                    return;
                }

                PropostaRequest request = parseRequest(jsonPayload);

                if (!ValidadorSeguranca.validarAcesso(token, request.plano())) {
                    LoggerSaaS.log("WARN", "Acesso negado: token invalido ou plano insuficiente.");
                    enviarResposta(exchange, 403, jsonErro("Acesso negado: token invalido ou plano insuficiente"));
                    return;
                }

                motor.processarAsync(request).thenAccept(response -> {
                    try {
                        LoggerSaaS.log("SUCCESS", "Calculo finalizado para ID: " + response.idProposta());
                        dao.persistir("ID: " + response.idProposta() + " | Valor: " + response.valorLiquido());
                        enviarResposta(exchange, 200, jsonSucesso(response));
                    } catch (IOException e) {
                        LoggerSaaS.log("ERROR", "Falha ao enviar resposta: " + e.getMessage());
                    }
                }).exceptionally(ex -> {
                    Throwable causa = (ex instanceof CompletionException && ex.getCause() != null)
                        ? ex.getCause()
                        : ex;
                    int status = (causa instanceof IllegalArgumentException) ? 400 : 500;
                    try {
                        enviarResposta(exchange, status, jsonErro(causa.getMessage()));
                    } catch (IOException e) {
                        LoggerSaaS.log("ERROR", "Falha ao enviar erro: " + e.getMessage());
                    }
                    return null;
                });
            } catch (IllegalArgumentException e) {
                enviarResposta(exchange, 400, jsonErro(e.getMessage()));
            } catch (Exception e) {
                enviarResposta(exchange, 500, jsonErro("Falha interna"));
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

        private String jsonErro(String mensagem) {
            return "{\"error\":\"" + JsonUtils.escapeJson(mensagem) + "\"}";
        }

        private void enviarResposta(HttpExchange exchange, int code, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    static class OrcamentoDAO {
        private final List<String> tabelaOrcamentos = new CopyOnWriteArrayList<>();

        public void persistir(String dadosOrcamento) {
            tabelaOrcamentos.add(dadosOrcamento);
            LoggerSaaS.log("INFO", "[DB-JAVA] Registro financeiro arquivado: " + dadosOrcamento);
        }
    }
}
