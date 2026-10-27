package br.com.coti.atendimentos.aplicacao.porta;

import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import java.util.List;

/**
 * Busca de conhecimento na base da COTI. A única implementação chama a ferramenta MCP {@code buscar_conhecimento};
 * não há outro caminho para a base (Artigo 4). Lista vazia é resultado válido.
 */
public interface BaseConhecimentoPort {

    /**
     * @param perguntaMascarada texto já sem dado pessoal; é o único que sai para a api-conhecimento
     * @param limite quantidade máxima de trechos
     * @throws br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException timeout, conexão,
     *         credencial recusada, {@code isError} ou resultado fora do contrato
     * @throws br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException se o prazo do atendimento acabar
     */
    List<TrechoConhecimento> buscar(String perguntaMascarada, int limite, Prazo prazo);
}
