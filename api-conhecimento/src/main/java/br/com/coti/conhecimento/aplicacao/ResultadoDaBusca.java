package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import java.time.Duration;
import java.util.List;

/**
 * Resultado do caso de uso de busca (RF04). A lista vazia é um resultado válido. O limiar, o modelo e a duração
 * existem para o endpoint de diagnóstico (RF12); a ferramenta MCP devolve só os trechos.
 */
public record ResultadoDaBusca(List<TrechoEncontrado> trechos, double limiarSimilaridade, String modeloEmbedding,
                               Duration duracao) {

    public ResultadoDaBusca {
        trechos = List.copyOf(trechos);
    }

    public int total() {
        return trechos.size();
    }
}
