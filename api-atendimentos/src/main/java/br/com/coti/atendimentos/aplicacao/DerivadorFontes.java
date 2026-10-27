package br.com.coti.atendimentos.aplicacao;

import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.Fonte;
import br.com.coti.atendimentos.dominio.ReferenciaTrecho;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deriva as fontes da resposta (contrato, seção 6.4): para cada referência válida, sem repetição, copia os campos
 * do trecho do resultado MCP, na ordem do contexto (similaridade decrescente). Nenhum campo vem do texto do
 * modelo, os trechos não citados não aparecem e os rótulos {@code T<n>} não saem na fonte.
 */
public final class DerivadorFontes {

    public List<Fonte> derivar(List<String> referencias, ContextoRag contexto) {
        Set<String> citadas = new HashSet<>(referencias);
        return contexto.referencias().stream()
                .filter(r -> citadas.contains(r.rotulo()))
                .map(ReferenciaTrecho::trecho)
                .map(Fonte::de)
                .distinct()
                .toList();
    }
}
