package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Documento;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recolhe documentos que ficaram {@code PROCESSANDO} porque o processo caiu no meio do cadastro: apaga os trechos
 * que tenham sobrado e marca o documento {@code ERRO_PROCESSAMENTO}. Só mexe em documento mais antigo que o
 * tempo máximo de processamento, para não atropelar um cadastro em andamento (em outra instância, por exemplo).
 */
public final class RecolherDocumentosInterrompidosService {

    private static final Logger log = LoggerFactory.getLogger(RecolherDocumentosInterrompidosService.class);

    private final RepositorioDocumentoPort documentos;
    private final RepositorioVetorialPort vetorial;
    private final Duration tempoMaximo;
    private final Clock relogio;

    public RecolherDocumentosInterrompidosService(RepositorioDocumentoPort documentos,
            RepositorioVetorialPort vetorial, Duration tempoMaximo, Clock relogio) {
        this.documentos = documentos;
        this.vetorial = vetorial;
        this.tempoMaximo = tempoMaximo;
        this.relogio = relogio;
    }

    /** Devolve quantos documentos foram recolhidos. */
    public int recolher() {
        OffsetDateTime limite = OffsetDateTime.now(relogio).minus(tempoMaximo);
        int recolhidos = 0;
        for (Documento interrompido : documentos.buscarProcessandoAnterioresA(limite)) {
            vetorial.removerPorDocumento(interrompido.id());
            documentos.atualizarEstado(interrompido.falharProcessamento());
            log.warn("Cadastro interrompido recolhido. conhecimentoId={}", interrompido.id());
            recolhidos++;
        }
        return recolhidos;
    }
}
