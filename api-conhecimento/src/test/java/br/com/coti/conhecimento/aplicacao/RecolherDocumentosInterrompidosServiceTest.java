package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecolherDocumentosInterrompidosServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneOffset.UTC);

    private final RepositorioDocumentoEmMemoria repositorio = new RepositorioDocumentoEmMemoria();
    private final RepositorioVetorialPort vetorial = mock(RepositorioVetorialPort.class);
    private final RecolherDocumentosInterrompidosService servico = new RecolherDocumentosInterrompidosService(
            repositorio, vetorial, Duration.ofMinutes(10), RELOGIO);

    private Documento processando(String chave, long minutosAtras) {
        Documento documento = Documento.emProcessamento(UUID.randomUUID(), "Título " + chave, "Conteúdo " + chave,
                "Fonte", Categoria.CURSO, HashConteudo.sha256(chave), "modelo", null, "gestor",
                OffsetDateTime.now(RELOGIO).minusMinutes(minutosAtras));
        repositorio.inserir(documento);
        return documento;
    }

    @Test
    void recolheSoOQueEstaProcessandoHaMaisDoQueOTempoMaximo() {
        Documento velho = processando("velho", 11);
        Documento recente = processando("recente", 9);

        int recolhidos = servico.recolher();

        assertThat(recolhidos).isEqualTo(1);
        assertThat(repositorio.documentos.get(velho.id()).status()).isEqualTo(StatusDocumento.ERRO_PROCESSAMENTO);
        assertThat(repositorio.documentos.get(recente.id()).status()).isEqualTo(StatusDocumento.PROCESSANDO);
        verify(vetorial).removerPorDocumento(velho.id());
        verify(vetorial, never()).removerPorDocumento(recente.id());
    }

    @Test
    void naoMexeEmDocumentoDeOutroEstado() {
        Documento publicado = processando("publicado", 60);
        repositorio.atualizarEstado(publicado.publicar(3));

        assertThat(servico.recolher()).isZero();

        assertThat(repositorio.documentos.get(publicado.id()).status()).isEqualTo(StatusDocumento.PUBLICADO);
        verify(vetorial, never()).removerPorDocumento(publicado.id());
    }

    @Test
    void semInterrompidosNaoFazNada() {
        assertThat(servico.recolher()).isZero();
        assertThat(repositorio.atualizacoes).isZero();
    }

    @Test
    void ofimDoPrazoEExclusivo() {
        Documento noLimite = processando("limite", 10);

        assertThat(servico.recolher()).isZero();
        assertThat(repositorio.documentos.get(noLimite.id()).status()).isEqualTo(StatusDocumento.PROCESSANDO);
    }
}
