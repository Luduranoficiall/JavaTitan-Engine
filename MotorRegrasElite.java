import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;

public class MotorRegrasElite {
    // Um mapa de funções (estratégias modernas) usando BigDecimal para precisão financeira.
    // A chave é o nome do plano, e o valor é a própria lógica de cálculo.
    private static final Map<String, Function<BigDecimal, BigDecimal>> REGRAS = Map.of(
        "VIP",     valor -> valor.multiply(new BigDecimal("0.98")), // Taxa de 2%
        "STARTER", valor -> valor.multiply(new BigDecimal("0.90")), // Taxa de 10%
        "PRO",     valor -> valor.multiply(new BigDecimal("0.95"))  // Taxa de 5%
    );

    public static BigDecimal processar(String plano, BigDecimal valor) {
        // getOrDefault garante que se o plano não existir, uma função de identidade (que retorna o próprio valor) é usada.
        // .apply() executa a função (estratégia) encontrada.
        Function<BigDecimal, BigDecimal> regra = REGRAS.getOrDefault(plano.toUpperCase(), Function.identity());
        return regra.apply(valor).setScale(2, RoundingMode.HALF_UP);
    }

    public static void main(String[] args) {
        BigDecimal valorBase = new BigDecimal("1000.00");
        System.out.println("--- [ARQUITETURA] Demonstração do Strategy Pattern (Java Funcional) ---");
        System.out.println("💎 Resultado VIP: R$ " + processar("VIP", valorBase));
        System.out.println("🚀 Resultado Starter: R$ " + processar("STARTER", valorBase));
        System.out.println("📈 Resultado PRO: R$ " + processar("PRO", valorBase));
        System.out.println("❓ Resultado Plano Inexistente: R$ " + processar("BASIC", valorBase));
        System.out.println("--------------------------------------------------------------------");
    }
}