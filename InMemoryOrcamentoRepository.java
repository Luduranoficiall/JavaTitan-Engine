import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryOrcamentoRepository implements OrcamentoRepository {
    private final List<Orcamento> tabela = new CopyOnWriteArrayList<>();

    @Override
    public void salvar(Orcamento orcamento) {
        tabela.add(orcamento);
        LoggerSaaS.log("INFO", "[DB-JAVA] Registro arquivado em memoria: " + orcamento.idProposta());
    }
}
