package com.javatitan.engine;

import java.math.BigDecimal;

public enum Plano {
    STARTER(new BigDecimal("0.06")),
    PRO(new BigDecimal("0.15")),
    VIP(new BigDecimal("0.02"));

    private final BigDecimal taxa;

    Plano(BigDecimal taxa) {
        this.taxa = taxa;
    }

    public BigDecimal taxa() {
        return taxa;
    }

    public static Plano from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Plano obrigatorio.");
        }
        try {
            return Plano.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Plano invalido: " + raw);
        }
    }
}
