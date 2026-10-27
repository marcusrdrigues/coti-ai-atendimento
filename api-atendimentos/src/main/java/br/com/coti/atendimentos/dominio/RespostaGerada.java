package br.com.coti.atendimentos.dominio;

import java.util.List;

/**
 * Resposta produzida pelo gerador, já validada. As fontes são os trechos da ferramenta MCP que sustentam o texto;
 * nunca vêm do modelo. {@code conhecimentoSuficiente = false} obriga a lista de citados vazia.
 */
public record RespostaGerada(String resposta, boolean conhecimentoSuficiente, List<TrechoConhecimento> citados) {

    public RespostaGerada {
        citados = List.copyOf(citados);
        if (conhecimentoSuficiente && citados.isEmpty()) {
            throw new IllegalArgumentException("conhecimento suficiente exige ao menos um trecho citado");
        }
        if (!conhecimentoSuficiente && !citados.isEmpty()) {
            throw new IllegalArgumentException("sem conhecimento suficiente, nenhum trecho é citado");
        }
    }
}
