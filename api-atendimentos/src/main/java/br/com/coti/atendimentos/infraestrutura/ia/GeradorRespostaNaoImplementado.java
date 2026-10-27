package br.com.coti.atendimentos.infraestrutura.ia;

import br.com.coti.atendimentos.aplicacao.porta.GeradorRespostaPort;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Gerador provisório da Fase 6: o modelo de IA só entra na Fase 7. Sempre falha, de forma explícita e segura
 * (503 MODELO_INDISPONIVEL), para que nunca saia uma resposta inventada no lugar da que ainda não existe.
 * Será substituído por {@code OpenAiGeradorRespostaAdapter}.
 */
@Component
public final class GeradorRespostaNaoImplementado implements GeradorRespostaPort {

    @Override
    public RespostaGerada gerar(String perguntaMascarada, List<TrechoConhecimento> trechos, Prazo prazo) {
        throw new ModeloIndisponivelException("A geração de respostas ainda não foi implementada (Fase 7).");
    }
}
