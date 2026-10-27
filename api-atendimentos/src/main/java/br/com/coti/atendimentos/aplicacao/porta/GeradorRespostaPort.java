package br.com.coti.atendimentos.aplicacao.porta;

import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;

/**
 * Geração da resposta ao aluno pelo modelo de IA, a partir da pergunta mascarada e do contexto já montado. Só é
 * chamada quando há pelo menos um trecho no contexto. A saída volta crua: quem a valida é o caso de uso.
 */
public interface GeradorRespostaPort {

    /**
     * @param motivoReprovacao {@code null} na primeira tentativa; nas seguintes, por que a saída anterior foi
     *        reprovada, para o modelo corrigir
     * @throws br.com.coti.atendimentos.dominio.excecao.SaidaModeloInvalidaException se a saída não vier no formato
     * @throws br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException provedor fora do ar, limite
     *         atingido, credencial recusada
     * @throws br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException se o prazo acabar
     */
    RespostaGerada gerar(String perguntaMascarada, ContextoRag contexto, String motivoReprovacao, Prazo prazo);
}
