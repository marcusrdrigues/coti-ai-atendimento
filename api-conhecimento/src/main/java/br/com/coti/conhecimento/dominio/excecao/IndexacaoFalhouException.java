package br.com.coti.conhecimento.dominio.excecao;

import java.util.UUID;

/**
 * Falha ao dividir, gerar embeddings ou gravar os trechos de um documento. O documento fica
 * {@code ERRO_PROCESSAMENTO}, sem trechos, e o gestor pode consultá-lo pelo identificador.
 */
public class IndexacaoFalhouException extends RuntimeException {

    private final UUID conhecimentoId;

    public IndexacaoFalhouException(UUID conhecimentoId, Throwable causa) {
        super("Falha ao indexar o conhecimento", causa);
        this.conhecimentoId = conhecimentoId;
    }

    public UUID conhecimentoId() {
        return conhecimentoId;
    }
}
