import java.time.LocalDateTime;

public class LoggerSaaS {
    public static void log(String level, String message) {
        String timestamp = LocalDateTime.now().toString();
        // Formato estruturado: [TIMESTAMP] [LEVEL] MESSAGE
        System.out.printf("[%s] [%s] %s%n", timestamp, level, message);
    }

    public static void main(String[] args) {
        log("INFO", "Iniciando Motor Financeiro v2.0...");
        log("DEBUG", "Porta 8080 configurada para escuta.");
        
        try {
            // Simulação de processamento
            log("SUCCESS", "Cálculo VIP processado em 14ms.");
        } catch (Exception e) {
            log("ERROR", "Falha crítica: " + e.getMessage());
        }
    }
}