package com.javatitan.engine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class JdbcOrcamentoRepository implements OrcamentoRepository {
    private final DbConfig config;

    public JdbcOrcamentoRepository(DbConfig config) {
        this.config = config;
        carregarDriver();
        inicializarSchema();
    }

    @Override
    public void salvar(Orcamento orcamento) {
        String sql = "INSERT INTO orcamentos (" +
            "id_proposta, id_cliente, plano, valor_bruto, taxa_aplicada, valor_liquido, status, criado_em" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, orcamento.idProposta().toString());
            stmt.setString(2, orcamento.idCliente().toString());
            stmt.setString(3, orcamento.plano().name());
            stmt.setBigDecimal(4, orcamento.valorBruto());
            stmt.setBigDecimal(5, orcamento.taxaAplicada());
            stmt.setBigDecimal(6, orcamento.valorLiquido());
            stmt.setString(7, orcamento.status());
            stmt.setTimestamp(8, Timestamp.from(orcamento.criadoEm()));
            stmt.executeUpdate();
            LoggerSaaS.log("INFO", "[DB-JAVA] Registro gravado no banco: " + orcamento.idProposta());
        } catch (SQLException ex) {
            throw new IllegalStateException("Falha ao persistir no banco: " + ex.getMessage(), ex);
        }
    }

    private void inicializarSchema() {
        String sql = "CREATE TABLE IF NOT EXISTS orcamentos (" +
            "id_proposta VARCHAR(36) PRIMARY KEY," +
            "id_cliente VARCHAR(36) NOT NULL," +
            "plano VARCHAR(16) NOT NULL," +
            "valor_bruto DECIMAL(19,2) NOT NULL," +
            "taxa_aplicada DECIMAL(19,2) NOT NULL," +
            "valor_liquido DECIMAL(19,2) NOT NULL," +
            "status VARCHAR(32) NOT NULL," +
            "criado_em TIMESTAMP NOT NULL" +
            ")";

        try (Connection conn = abrirConexao();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Falha ao inicializar schema: " + ex.getMessage(), ex);
        }
    }

    private Connection abrirConexao() throws SQLException {
        if (config.user() == null) {
            return DriverManager.getConnection(config.url());
        }
        String password = (config.password() == null) ? "" : config.password();
        return DriverManager.getConnection(config.url(), config.user(), password);
    }

    private void carregarDriver() {
        if (config.driver() == null) {
            return;
        }
        try {
            Class.forName(config.driver());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Driver JDBC nao encontrado: " + config.driver(), ex);
        }
    }
}
