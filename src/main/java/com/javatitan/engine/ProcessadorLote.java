package com.javatitan.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

record Proposta(String id, Plano plano, BigDecimal valor) {}

public class ProcessadorLote {
    public static void main(String[] args) {
        List<Proposta> propostas = List.of(
            new Proposta("001", Plano.STARTER, new BigDecimal("1500.00")),
            new Proposta("002", Plano.PRO, new BigDecimal("4500.00")),
            new Proposta("003", Plano.VIP, new BigDecimal("12000.00"))
        );

        System.out.println("[JAVA] Processando lote com regras por plano...");

        List<String> resultados = propostas.stream()
            .filter(p -> p.valor().compareTo(new BigDecimal("2000")) > 0)
            .map(p -> {
                BigDecimal valorLiquido = MotorRegrasElite.processar(p.plano(), p.valor());
                return "ID: " + p.id() + " | Plano: " + p.plano() + " | Liq: R$ "
                    + String.format("%.2f", valorLiquido);
            })
            .collect(Collectors.toList());

        resultados.forEach(System.out::println);
    }
}
