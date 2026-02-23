package com.javatitan.engine;

public interface OrcamentoRepository extends AutoCloseable {
    void salvar(Orcamento orcamento);

    @Override
    default void close() {
    }
}
