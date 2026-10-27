package br.com.coti.conhecimento.aplicacao.porta;

import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import java.time.LocalDate;
import java.util.List;

/**
 * Busca por similaridade no banco vetorial. O adaptador gera o embedding da pergunta e aplica, dentro da própria
 * consulta, o filtro de publicação, modelo e vigência, o limiar e o {@code topK} (RN04.2). Devolve os trechos já
 * com a similaridade de 0 a 1.
 *
 * @throws br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException se o embedding da pergunta falhar
 * @throws br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException se o banco falhar
 */
public interface BuscaVetorialPort {

    List<TrechoEncontrado> buscar(Consulta consulta);

    /** Parâmetros da busca: só trechos {@code PUBLICADO}, do modelo informado e vigentes em {@code hoje}. */
    record Consulta(String pergunta, int limite, double limiarSimilaridade, String modeloEmbedding, LocalDate hoje) {

        /** Não expõe a pergunta em logs nem em mensagens de erro. */
        @Override
        public String toString() {
            return "Consulta[limite=" + limite + ", limiarSimilaridade=" + limiarSimilaridade + "]";
        }
    }
}
