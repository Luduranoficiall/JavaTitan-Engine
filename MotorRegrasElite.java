import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public class MotorRegrasElite {
    private static final Map<Plano, Function<BigDecimal, BigDecimal>> REGRAS = new EnumMap<>(Plano.class);

    static {
        REGRAS.put(Plano.VIP, valor -> aplicarDesconto(valor, Plano.VIP));
        REGRAS.put(Plano.STARTER, valor -> aplicarDesconto(valor, Plano.STARTER));
        REGRAS.put(Plano.PRO, valor -> aplicarDesconto(valor, Plano.PRO));
    }

    public static BigDecimal processar(String plano, BigDecimal valor) {
        return processar(Plano.from(plano), valor);
    }

    public static BigDecimal processar(Plano plano, BigDecimal valor) {
        Function<BigDecimal, BigDecimal> regra = REGRAS.getOrDefault(plano, Function.identity());
        return regra.apply(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal aplicarDesconto(BigDecimal valor, Plano plano) {
        BigDecimal fator = BigDecimal.ONE.subtract(plano.taxa());
        return valor.multiply(fator);
    }

    public static void main(String[] args) {
        BigDecimal valorBase = new BigDecimal("1000.00");
        System.out.println("--- [ARQUITETURA] Strategy Pattern com Enum + Funcoes ---");
        System.out.println("VIP: R$ " + processar(Plano.VIP, valorBase));
        System.out.println("STARTER: R$ " + processar(Plano.STARTER, valorBase));
        System.out.println("PRO: R$ " + processar(Plano.PRO, valorBase));
        System.out.println("------------------------------------------------------");
    }
}
