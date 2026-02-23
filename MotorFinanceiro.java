import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Record: Imutabilidade nativa e concisão para DTOs
record PropostaRequest(UUID idCliente, BigDecimal valorBruto, String plano) {}

record PropostaResponse(UUID idProposta, BigDecimal valorLiquido, BigDecimal taxaAplicada, String status) {}

class MotorFinanceiroEspecialista {
    // Regras de Negócio isoladas (Domain Logic)
    // MÉTODO ASSÍNCRONO: Usa CompletableFuture para não bloquear a thread do servidor.
    public CompletableFuture<PropostaResponse> processarAsync(PropostaRequest request) {
        // supplyAsync executa o código em uma thread separada do ForkJoinPool.
        return CompletableFuture.supplyAsync(() -> {
            LoggerSaaS.log("INFO", "[JAVA-THREAD] Iniciando cálculo de alta performance para cliente: " + request.idCliente());
            try {
                Thread.sleep(2000); // Simula cálculo complexo ou I/O (ex: consulta a outro serviço)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            if (request.valorBruto().signum() < 0) {
                throw new IllegalArgumentException("Valor bruto não pode ser negativo.");
            }
            BigDecimal taxa = calcularTaxaPorPlano(request.plano());
            BigDecimal valorTaxa = request.valorBruto().multiply(taxa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal valorLiquido = request.valorBruto().subtract(valorTaxa);

            return new PropostaResponse(UUID.randomUUID(), valorLiquido, valorTaxa, "PROCESSADO_ASYNC");
        });
    }

    private BigDecimal calcularTaxaPorPlano(String plano) {
        // Switch Expressions (Java 14+) para código limpo
        return switch (plano.toUpperCase()) {
            case "STARTER" -> new BigDecimal("0.06"); // 6%
            case "PRO" -> new BigDecimal("0.15");     // 15%
            default -> BigDecimal.ZERO;
        };
    }
}

public class MotorFinanceiro {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/calcular", new CalculoHandler());
        server.createContext("/health", new HealthCheckHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        LoggerSaaS.log("INFO", "[MOTOR FINANCEIRO JAVA] Servidor de alta performance iniciado na porta 8080.");
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }
            String response = "{\"status\":\"UP\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    static class CalculoHandler implements HttpHandler {
        private final MotorFinanceiroEspecialista motor = new MotorFinanceiroEspecialista();
        private final OrcamentoDAO dao = new OrcamentoDAO();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarResposta(exchange, 405, "{\"error\":\"Método não permitido\"}");
                return;
            }

            // --- [PROJETO 7] INÍCIO DA VALIDAÇÃO DE SEGURANÇA ---
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                LoggerSaaS.log("WARN", "Acesso negado: Header de autorização ausente ou malformado.");
                enviarResposta(exchange, 401, "{\"error\":\"Header de autorização 'Bearer' é obrigatório\"}");
                return;
            }
            String token = authHeader.substring(7); // Remove "Bearer "
            // --- FIM DA VALIDAÇÃO DE SEGURANÇA ---

            try (InputStream is = exchange.getRequestBody()) {
                LoggerSaaS.log("INFO", "Recebendo nova requisição de cálculo...");
                String jsonPayload = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                // Desserialização manual para manter o projeto "vanilla"
                final PropostaRequest request = parseRequest(jsonPayload);

                // --- [PROJETO 7] VERIFICAÇÃO DE PERMISSÃO (CLAIM) ---
                if (!ValidadorSeguranca.validarAcesso(token, request.plano())) {
                    LoggerSaaS.log("WARN", "Acesso negado: Token inválido ou plano insuficiente para a operação.");
                    enviarResposta(exchange, 403, "{\"error\":\"Acesso negado: token inválido ou plano insuficiente\"}");
                    return;
                }

                // O handler retorna imediatamente, a thread é liberada para novas requisições.
                // A resposta é enviada quando o 'Future' completar.
                motor.processarAsync(request).thenAccept(response -> {
                    try {
                        LoggerSaaS.log("SUCCESS", "[JAVA-THREAD] Cálculo finalizado para ID: " + response.idProposta() + ". Enviando resposta.");
                        // --- [PROJETO 8] PERSISTÊNCIA VIA DAO ---
                        dao.persistir("ID: " + response.idProposta() + " | Valor: " + response.valorLiquido());
                        String jsonResponse = String.format(
                            "{\"idProposta\":\"%s\",\"valorLiquido\":%.2f,\"taxaAplicada\":%.2f,\"status\":\"%s\"}",
                            response.idProposta(), response.valorLiquido(), response.taxaAplicada(), response.status()
                        );
                        enviarResposta(exchange, 200, jsonResponse);
                    } catch (IOException e) {
                        LoggerSaaS.log("ERROR", "Falha ao enviar resposta assíncrona: " + e.getMessage());
                    }
                }).exceptionally(ex -> {
                    LoggerSaaS.log("ERROR", "Falha crítica no processamento assíncrono: " + ex.getCause().getMessage());
                    try {
                        enviarResposta(exchange, 500, "{\"error\":\"" + ex.getCause().getMessage() + "\"}");
                    } catch (IOException e) { /* ignore */ }
                    return null;
                });

            } catch (Exception e) {
                // Erros que acontecem antes do Future (ex: parsing do JSON)
                LoggerSaaS.log("ERROR", "Falha na preparação da requisição (ex: JSON malformado): " + e.getMessage());
                enviarResposta(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private PropostaRequest parseRequest(String json) {
            // Regex para extrair valores de um JSON simples. Em produção real, uma lib como Gson seria usada.
            UUID id = UUID.fromString(extrairValor(json, "idCliente"));
            BigDecimal valor = new BigDecimal(extrairValor(json, "valorBruto"));
            String plano = extrairValor(json, "plano");
            return new PropostaRequest(id, valor, plano);
        }

        private String extrairValor(String json, String chave) {
            Matcher matcher = Pattern.compile("\"" + chave + "\":\"?([^\",}]+)\"?").matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new IllegalArgumentException("Chave não encontrada no JSON: " + chave);
        }

        private void enviarResposta(HttpExchange exchange, int code, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    // --- [PROJETO 8] DAO (Data Access Object) ---
    static class OrcamentoDAO {
        private final List<String> tabelaOrcamentos = new ArrayList<>();

        public void persistir(String dadosOrcamento) {
            tabelaOrcamentos.add(dadosOrcamento);
            LoggerSaaS.log("INFO", "[DB-JAVA] Registro financeiro arquivado: " + dadosOrcamento);
        }
    }

    // Classe utilitária de segurança, conforme definido no Projeto 7
    static class ValidadorSeguranca {
        public static boolean validarAcesso(String token, String planoNecessario) {
            try {
                String[] partes = token.split("\\.");
                if (partes.length < 2) return false;
                String payload = new String(Base64.getDecoder().decode(partes[1]));
                LoggerSaaS.log("DEBUG", "[JAVA-AUTH] Validando permissões no payload: " + payload);
                return payload.contains("\"plan\":\"" + planoNecessario.toUpperCase() + "\"");
            } catch (Exception e) {
                LoggerSaaS.log("ERROR", "Falha ao decodificar ou validar token JWT: " + e.getMessage());
                return false;
            }
        }
    }
}