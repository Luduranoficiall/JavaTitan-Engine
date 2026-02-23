import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Orcamento(
    UUID idProposta,
    UUID idCliente,
    Plano plano,
    BigDecimal valorBruto,
    BigDecimal taxaAplicada,
    BigDecimal valorLiquido,
    String status,
    Instant criadoEm
) {}
