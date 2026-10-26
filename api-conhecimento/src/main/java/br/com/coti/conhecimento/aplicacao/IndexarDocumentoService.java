package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.Trecho;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grava os trechos de um documento no banco vetorial, em lotes, repetindo apenas falhas transitórias do serviço
 * de embeddings. Os trechos ficam {@code PROCESSANDO} (invisíveis à busca) até a publicação (ADR-07). Em falha
 * definitiva sobe a exceção e quem chamou compensa; este serviço não apaga nada por conta própria.
 */
public final class IndexarDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(IndexarDocumentoService.class);

    private final RepositorioVetorialPort vetorial;
    private final PoliticaNovasTentativas novasTentativas;
    private final int tamanhoLote;

    public IndexarDocumentoService(RepositorioVetorialPort vetorial, PoliticaNovasTentativas novasTentativas,
                                   int tamanhoLote) {
        if (tamanhoLote < 1) {
            throw new IllegalArgumentException("tamanhoLote deve ser maior que zero");
        }
        this.vetorial = vetorial;
        this.novasTentativas = novasTentativas;
        this.tamanhoLote = tamanhoLote;
    }

    public void indexar(Documento documento, List<Trecho> trechos) {
        int numeroDoLote = 0;
        for (int inicio = 0; inicio < trechos.size(); inicio += tamanhoLote) {
            List<Trecho> lote = trechos.subList(inicio, Math.min(inicio + tamanhoLote, trechos.size()));
            numeroDoLote++;
            novasTentativas.executar(() -> {
                vetorial.gravarLote(documento, lote);
                return null;
            });
            log.debug("Lote gravado. conhecimentoId={} lote={} trechos={}", documento.id(), numeroDoLote, lote.size());
        }
    }
}
