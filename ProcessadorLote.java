import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// Record: Dados imutáveis (Padrão Sênior)
record Proposta(String id, BigDecimal valor) {}

public class ProcessadorLote {
    public static void main(String[] args) {
        // Simulando dados que viriam do C#
        List<Proposta> propostas = List.of(
            new Proposta("001", new BigDecimal("1500.0")),
            new Proposta("002", new BigDecimal("4500.0")),
            new Proposta("003", new BigDecimal("12000.0"))
        );

        System.out.println("[JAVA] Processando Lote de Elite...");

        // Usando Streams para filtrar e calcular impostos (10%)
        List<String> resultados = propostas.stream()
            .filter(p -> p.valor().compareTo(new BigDecimal("2000")) > 0) // Foca só nos contratos grandes
            .map(p -> {
                BigDecimal valorLiquido = p.valor().multiply(new BigDecimal("0.90"));
                return "ID: " + p.id() + " | Liq: R$ " + String.format("%.2f", valorLiquido);
            })
            .collect(Collectors.toList());

        resultados.forEach(System.out::println);
    }
}
