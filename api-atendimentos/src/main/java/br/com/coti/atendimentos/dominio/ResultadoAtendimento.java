package br.com.coti.atendimentos.dominio;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resultado de um atendimento (RF09 e RF10). Sem evidência: {@code conhecimentoSuficiente = false} e
 * {@code fontes} vazio. O identificador do atendimento é o id de correlação da requisição e vem da camada HTTP.
 */
public record ResultadoAtendimento(String resposta, boolean conhecimentoSuficiente, List<Fonte> fontes,
                                   OffsetDateTime geradoEm) {

    public ResultadoAtendimento {
        fontes = List.copyOf(fontes);
        if (!conhecimentoSuficiente && !fontes.isEmpty()) {
            throw new IllegalArgumentException("sem conhecimento suficiente, as fontes devem ser vazias");
        }
    }

    public static ResultadoAtendimento semEvidencia(String mensagem, OffsetDateTime geradoEm) {
        return new ResultadoAtendimento(mensagem, false, List.of(), geradoEm);
    }
}
