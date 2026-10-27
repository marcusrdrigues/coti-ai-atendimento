package br.com.coti.atendimentos.aplicacao.porta;

import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import java.util.List;

/**
 * Geração da resposta ao aluno a partir da pergunta mascarada e dos trechos recuperados. Só é chamada quando há
 * pelo menos um trecho. A implementação com o modelo de IA entra na Fase 7; a porta fica declarada aqui para o
 * fluxo e os testes não dependerem do SDK de IA (Artigo 3).
 */
public interface GeradorRespostaPort {

    /**
     * @throws br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException provedor fora do ar, limite
     *         atingido, credencial recusada
     * @throws br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException se o prazo acabar
     */
    RespostaGerada gerar(String perguntaMascarada, List<TrechoConhecimento> trechos, Prazo prazo);
}
