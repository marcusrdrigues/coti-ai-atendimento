package br.com.coti.atendimentos.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.coti.atendimentos.aplicacao.ResponderPerguntaService.MensagensSemEvidencia;
import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.aplicacao.porta.GeradorRespostaPort;
import br.com.coti.atendimentos.aplicacao.porta.MascaradorPort;
import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.Fonte;
import br.com.coti.atendimentos.dominio.PerguntaAluno;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.ResultadoAtendimento;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.ModeloIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResponderPerguntaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final MensagensSemEvidencia MENSAGENS = new MensagensSemEvidencia(
            "Não encontrei essa informação nos materiais oficiais da COTI.", "Fale conosco: https://www.coti.com.br/contato");

    /** Relógio que anda só quando o teste manda. */
    private static final class RelogioManual extends Clock {
        private Instant agora = AGORA;

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final MascaradorPort mascarador = mock(MascaradorPort.class);
    private final BaseConhecimentoPort base = mock(BaseConhecimentoPort.class);
    private final GeradorRespostaPort gerador = mock(GeradorRespostaPort.class);
    private final RelogioManual relogio = new RelogioManual();
    private final ResponderPerguntaService servico = new ResponderPerguntaService(mascarador, base, gerador, MENSAGENS,
            5, Duration.ofSeconds(30), relogio);

    private static TrechoConhecimento trecho(String texto, double similaridade) {
        return new TrechoConhecimento(UUID.randomUUID(), texto, UUID.randomUUID(), "Título", "Fonte",
                Categoria.CURSO, similaridade);
    }

    private PerguntaAluno pergunta(String texto) {
        when(mascarador.mascarar(anyString())).thenAnswer(i -> i.getArgument(0));
        return new PerguntaAluno(texto);
    }

    // ---- mascaramento

    @Test
    void oTextoMascaradoEOUnicoQueSaiParaABaseEParaOGerador() {
        TrechoConhecimento t = trecho("Texto", 0.9);
        when(mascarador.mascarar("meu cpf é 529.982.247-25, posso?")).thenReturn("meu cpf é [CPF], posso?");
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(t));
        when(gerador.gerar(anyString(), any(), any())).thenReturn(new RespostaGerada("Sim.", true, List.of(t)));

        servico.responder(new PerguntaAluno("meu cpf é 529.982.247-25, posso?"));

        verify(base).buscar(eq("meu cpf é [CPF], posso?"), eq(5), any(Prazo.class));
        verify(gerador).gerar(eq("meu cpf é [CPF], posso?"), eq(List.of(t)), any(Prazo.class));
    }

    @Test
    void mascaradoMaisLongoQueMilCaracteresEhLimitadoParaOServidorAceitar() {
        when(mascarador.mascarar(anyString())).thenReturn("x".repeat(1_040));
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of());

        servico.responder(new PerguntaAluno("abc"));

        ArgumentCaptor<String> enviada = ArgumentCaptor.forClass(String.class);
        verify(base).buscar(enviada.capture(), anyInt(), any());
        assertThat(enviada.getValue()).hasSize(1_000);
    }

    // ---- sem evidência

    @Test
    void semTrechosResponde200SemEvidenciaEOGeradorNuncaEChamado() {
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of());

        ResultadoAtendimento resultado = servico.responder(pergunta("a COTI oferece bolsa de 100%?"));

        assertThat(resultado.conhecimentoSuficiente()).isFalse();
        assertThat(resultado.fontes()).isEmpty();
        assertThat(resultado.resposta()).isEqualTo(
                "Não encontrei essa informação nos materiais oficiais da COTI. Fale conosco: https://www.coti.com.br/contato");
        verifyNoInteractions(gerador);
    }

    @Test
    void oGeradorIndicarConhecimentoInsuficienteTambemViraSemEvidenciaComATextoConfigurado() {
        TrechoConhecimento t = trecho("Texto", 0.9);
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(t));
        when(gerador.gerar(anyString(), any(), any())).thenReturn(new RespostaGerada("texto do modelo", false, List.of()));

        ResultadoAtendimento resultado = servico.responder(pergunta("pergunta qualquer"));

        assertThat(resultado.conhecimentoSuficiente()).isFalse();
        assertThat(resultado.fontes()).isEmpty();
        assertThat(resultado.resposta()).startsWith("Não encontrei").doesNotContain("texto do modelo");
    }

    // ---- com evidência

    @Test
    void comEvidenciaDevolveARespostaEAsFontesCopiadasDosTrechosCitados() {
        TrechoConhecimento a = trecho("A", 0.91);
        TrechoConhecimento b = trecho("B", 0.80);
        TrechoConhecimento naoCitado = trecho("C", 0.72);
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(a, b, naoCitado));
        when(gerador.gerar(anyString(), any(), any())).thenReturn(new RespostaGerada("Sim, assim.", true, List.of(a, b)));

        ResultadoAtendimento resultado = servico.responder(pergunta("pergunta qualquer"));

        assertThat(resultado.conhecimentoSuficiente()).isTrue();
        assertThat(resultado.resposta()).isEqualTo("Sim, assim.");
        assertThat(resultado.fontes()).containsExactly(Fonte.de(a), Fonte.de(b));
        assertThat(resultado.geradoEm()).isNotNull();
    }

    @Test
    void fonteRepetidaApareceUmaVezSo() {
        TrechoConhecimento a = trecho("A", 0.91);
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(a));
        when(gerador.gerar(anyString(), any(), any())).thenReturn(new RespostaGerada("Sim.", true, List.of(a, a)));

        assertThat(servico.responder(pergunta("pergunta qualquer")).fontes()).hasSize(1);
    }

    @Test
    void oPrazoEOMesmoEmTodoOFluxo() {
        TrechoConhecimento a = trecho("A", 0.9);
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(a));
        when(gerador.gerar(anyString(), any(), any())).thenReturn(new RespostaGerada("Sim.", true, List.of(a)));

        servico.responder(pergunta("pergunta qualquer"));

        ArgumentCaptor<Prazo> daBase = ArgumentCaptor.forClass(Prazo.class);
        ArgumentCaptor<Prazo> doGerador = ArgumentCaptor.forClass(Prazo.class);
        verify(base).buscar(anyString(), anyInt(), daBase.capture());
        verify(gerador).gerar(anyString(), any(), doGerador.capture());
        assertThat(daBase.getValue()).isSameAs(doGerador.getValue());
        assertThat(daBase.getValue().restante()).isEqualTo(Duration.ofSeconds(30));
    }

    // ---- falhas: nunca resposta parcial ou inventada

    @Test
    void falhaDaBasePropagaEOGeradorNaoEChamado() {
        when(base.buscar(anyString(), anyInt(), any())).thenThrow(new BaseConhecimentoIndisponivelException("fora"));

        assertThatThrownBy(() -> servico.responder(pergunta("pergunta qualquer")))
                .isInstanceOf(BaseConhecimentoIndisponivelException.class);
        verifyNoInteractions(gerador);
    }

    @Test
    void tempoEsgotadoNaBasePropaga() {
        when(base.buscar(anyString(), anyInt(), any())).thenThrow(new TempoEsgotadoException());

        assertThatThrownBy(() -> servico.responder(pergunta("pergunta qualquer")))
                .isInstanceOf(TempoEsgotadoException.class);
    }

    @Test
    void seOPrazoAcabarDepoisDaBuscaNaoChamaOGerador() {
        when(base.buscar(anyString(), anyInt(), any())).thenAnswer(i -> {
            relogio.avancar(Duration.ofSeconds(31));
            return List.of(trecho("A", 0.9));
        });

        assertThatThrownBy(() -> servico.responder(pergunta("pergunta qualquer")))
                .isInstanceOf(TempoEsgotadoException.class);
        verify(gerador, never()).gerar(anyString(), any(), any());
    }

    @Test
    void seOPrazoAcabarDuranteAGeracaoNaoDevolveResposta() {
        TrechoConhecimento a = trecho("A", 0.9);
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(a));
        when(gerador.gerar(anyString(), any(), any())).thenAnswer(i -> {
            relogio.avancar(Duration.ofSeconds(31));
            return new RespostaGerada("Sim.", true, List.of(a));
        });

        assertThatThrownBy(() -> servico.responder(pergunta("pergunta qualquer")))
                .isInstanceOf(TempoEsgotadoException.class);
    }

    @Test
    void falhaDoModeloPropaga() {
        when(base.buscar(anyString(), anyInt(), any())).thenReturn(List.of(trecho("A", 0.9)));
        when(gerador.gerar(anyString(), any(), any())).thenThrow(new ModeloIndisponivelException("fora"));

        assertThatThrownBy(() -> servico.responder(pergunta("pergunta qualquer")))
                .isInstanceOf(ModeloIndisponivelException.class);
    }

    @Test
    void asMensagensDeConfiguracaoNaoSaoAlteradasAlemDoEspacamento() {
        MensagensSemEvidencia comEspacos = new MensagensSemEvidencia("  Sem info.  ", "  Canal X  ");

        assertThat(comEspacos.texto()).isEqualTo("Sem info. Canal X");
    }
}
