package br.com.coti.atendimentos.infraestrutura.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.atendimentos.dominio.excecao.TempoEsgotadoException;
import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.ServidorMcpDeTeste;
import br.com.coti.atendimentos.suporte.TesteComServidorMcp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Adaptador MCP contra um servidor MCP de teste que segue o contrato: chamada determinística da ferramenta,
 * cabeçalhos de credencial e trace, validação do resultado, novas tentativas só para timeout e conexão, e prazo.
 */
class BaseConhecimentoMcpAdapterIT extends TesteComServidorMcp {

    private static final String PERGUNTA = "Preciso saber lógica de programação para o curso de Java?";

    @Autowired
    private BaseConhecimentoPort base;

    @Autowired
    private ApplicationContext contexto;

    private static Prazo prazoCheio() {
        return Prazo.iniciar(Clock.systemUTC(), Duration.ofSeconds(30));
    }

    private List<TrechoConhecimento> buscar() {
        return base.buscar(PERGUNTA, 5, prazoCheio());
    }

    // ---- chamada e conversão

    @Test
    void chamaAFerramentaComPerguntaELimiteEConverteOsTrechosParaODominio() {
        List<TrechoConhecimento> trechos = buscar();

        assertThat(SERVIDOR.chamadasDaFerramenta()).hasSize(1);
        assertThat(SERVIDOR.argumentosDaChamada(0).get("pergunta").asString()).isEqualTo(PERGUNTA);
        assertThat(SERVIDOR.argumentosDaChamada(0).get("limite").asInt()).isEqualTo(5);
        assertThat(trechos).hasSize(2);
        TrechoConhecimento primeiro = trechos.get(0);
        assertThat(primeiro.id()).isEqualTo(UUID.fromString("c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f"));
        assertThat(primeiro.documentoId()).isEqualTo(UUID.fromString("3f6c2a1e-8b4d-4c7a-9e1f-2b3c4d5e6f70"));
        assertThat(primeiro.titulo()).isEqualTo("Curso Java Fullstack — pré-requisitos");
        assertThat(primeiro.categoria()).isEqualTo(Categoria.PRE_REQUISITO);
        assertThat(primeiro.similaridade()).isEqualTo(0.86);
        assertThat(primeiro.texto()).contains("lógica de programação");
    }

    @Test
    void listaVaziaEhSucesso() {
        SERVIDOR.respondendoVazio();

        assertThat(buscar()).isEmpty();
    }

    @Test
    void naoRegistraNenhumToolCallbackParaOModelo() {
        assertThat(contexto.getBeansOfType(org.springframework.ai.tool.ToolCallbackProvider.class)).isEmpty();
    }

    // ---- cabeçalhos (T049)

    @Test
    void todaRequisicaoLevaACredencialDeServico() {
        buscar();

        assertThat(SERVIDOR.chamadasDaFerramenta()).isNotEmpty().allSatisfy(
                r -> assertThat(r.cabecalho("authorization")).isEqualTo("Bearer " + ChavesTeste.MCP));
    }

    @Test
    void levaOTraceparentDoAtendimentoQuandoHaTraceIdNoMdc() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        MDC.put("traceId", traceId);
        try {
            buscar();
        } finally {
            MDC.remove("traceId");
        }

        String traceparent = SERVIDOR.chamadasDaFerramenta().get(0).cabecalho("traceparent");
        assertThat(traceparent).matches("00-" + traceId + "-[0-9a-f]{16}-01");
    }

    @Test
    void cadaChamadaUsaUmSpanIdNovoNoMesmoTrace() {
        MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        try {
            buscar();
            buscar();
        } finally {
            MDC.remove("traceId");
        }

        List<String> spans = SERVIDOR.chamadasDaFerramenta().stream()
                .map(r -> r.cabecalho("traceparent").split("-")[2]).toList();
        assertThat(spans).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void semTraceIdNoMdcNaoInventaTraceparent() {
        buscar();

        assertThat(SERVIDOR.chamadasDaFerramenta().get(0).cabecalho("traceparent")).isNull();
    }

    // ---- resultado inválido: rejeitado por inteiro, sem nova tentativa

    private void assertRejeitaSemRetry() {
        assertThatThrownBy(this::buscar).isInstanceOf(BaseConhecimentoIndisponivelException.class);
        assertThat(SERVIDOR.chamadasDaFerramenta()).as("sem nova tentativa").hasSize(1);
    }

    @Test
    void isErrorViraBaseIndisponivelSemNovaTentativa() {
        SERVIDOR.respondendoComErroDaFerramenta("ARGUMENTOS_INVALIDOS");

        assertThatThrownBy(this::buscar).isInstanceOf(BaseConhecimentoIndisponivelException.class)
                .hasMessageContaining("codigo=ARGUMENTOS_INVALIDOS");
        assertThat(SERVIDOR.chamadasDaFerramenta()).hasSize(1);
    }

    @Test
    void semStructuredContentEhRejeitado() {
        SERVIDOR.respondendoSemStructuredContent();

        assertRejeitaSemRetry();
    }

    @Test
    void trechoSemCampoObrigatorioRejeitaOResultadoTodo() {
        SERVIDOR.respondendoComTrechos(ServidorMcpDeTeste.TRECHO_1,
                ServidorMcpDeTeste.TRECHO_2.replace("\"titulo\":\"Modalidades dos cursos\",", ""));

        assertRejeitaSemRetry();
    }

    @Test
    void categoriaDesconhecidaEhRejeitada() {
        SERVIDOR.respondendoComTrechos(ServidorMcpDeTeste.TRECHO_1.replace("PRE_REQUISITO", "OUTRA"));

        assertRejeitaSemRetry();
    }

    @Test
    void similaridadeForaDeZeroAUmEhRejeitada() {
        SERVIDOR.respondendoComTrechos(ServidorMcpDeTeste.TRECHO_1.replace("0.86", "1.7"));

        assertRejeitaSemRetry();
    }

    @Test
    void idRepetidoEhRejeitado() {
        SERVIDOR.respondendoComTrechos(ServidorMcpDeTeste.TRECHO_1, ServidorMcpDeTeste.TRECHO_1);

        assertRejeitaSemRetry();
    }

    @Test
    void maisDeDezTrechosEhRejeitado() {
        String[] onze = new String[11];
        for (int i = 0; i < onze.length; i++) {
            onze[i] = ServidorMcpDeTeste.TRECHO_1.replace("c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f", UUID.randomUUID().toString());
        }
        SERVIDOR.respondendoComTrechos(onze);

        assertRejeitaSemRetry();
    }

    // ---- falhas de autorização e de servidor: sem nova tentativa

    @Test
    void chaveDeServicoRecusadaCom401NaoTemNovaTentativa() {
        SERVIDOR.exigindoBearer("outra-chave");

        assertThatThrownBy(this::buscar).isInstanceOf(BaseConhecimentoIndisponivelException.class);
        assertThat(SERVIDOR.requisicoes()).as("uma única requisição, sem repetir").hasSize(1);
    }

    @Test
    void http403NaChamadaNaoTemNovaTentativa() {
        SERVIDOR.comStatusHttpNaChamada(403);

        assertRejeitaSemRetry();
    }

    @Test
    void http500NaChamadaTemNovasTentativasEViraBaseIndisponivel() {
        SERVIDOR.comStatusHttpNaChamada(500);

        assertThatThrownBy(this::buscar).isInstanceOf(BaseConhecimentoIndisponivelException.class);
        assertThat(SERVIDOR.chamadasDaFerramenta()).hasSize(3);
    }

    // ---- timeout: novas tentativas

    @Test
    void servidorLentoEsgotaAsTresTentativasEViraBaseIndisponivel() {
        SERVIDOR.demorandoNaChamada(Duration.ofSeconds(2));
        long inicio = System.nanoTime();

        assertThatThrownBy(this::buscar).isInstanceOf(BaseConhecimentoIndisponivelException.class)
                .hasMessageContaining("3 tentativas");

        assertThat(SERVIDOR.chamadasDaFerramenta()).hasSize(3);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void timeoutSeguidoDeSucessoDevolveOResultado() {
        SERVIDOR.demorandoAsPrimeirasChamadas(1, Duration.ofSeconds(2));

        List<TrechoConhecimento> trechos = buscar();

        assertThat(trechos).hasSize(2);
        assertThat(SERVIDOR.chamadasDaFerramenta()).hasSize(2);
    }

    // ---- servidor que reiniciou (sessão MCP perdida)

    @Test
    void servidorQueReiniciouPerdeASessaoEAMesmaPerguntaSeRecuperaSozinhaComUmaNovaTentativa() {
        buscar();
        SERVIDOR.esquecendoAsSessoes();

        List<TrechoConhecimento> trechos = buscar();

        assertThat(trechos).hasSize(2);
        assertThat(SERVIDOR.requisicoes()).as("reabriu a sessão com um novo initialize")
                .filteredOn(r -> "initialize".equals(r.metodoRpc())).hasSize(1);
    }

    // ---- prazo

    @Test
    void prazoCurtoInterrompeAsTentativasComTempoEsgotado() {
        SERVIDOR.demorandoNaChamada(Duration.ofSeconds(2));
        Prazo curto = Prazo.iniciar(Clock.systemUTC(), Duration.ofMillis(150));

        assertThatThrownBy(() -> base.buscar(PERGUNTA, 5, curto)).isInstanceOf(TempoEsgotadoException.class);

        assertThat(SERVIDOR.chamadasDaFerramenta()).as("a primeira tentativa já usou o prazo").hasSize(1);
    }

    @Test
    void prazoJaExpiradoNaoChamaOServidor() throws Exception {
        Prazo prazo = Prazo.iniciar(Clock.systemUTC(), Duration.ofMillis(5));
        Thread.sleep(30);

        assertThatThrownBy(() -> base.buscar(PERGUNTA, 5, prazo)).isInstanceOf(TempoEsgotadoException.class);

        assertThat(SERVIDOR.requisicoes()).isEmpty();
    }

    @Test
    void aPerguntaNaoApareceNaMensagemDasExcecoes() {
        SERVIDOR.respondendoSemStructuredContent();

        assertThatThrownBy(this::buscar).satisfies(e -> assertThat(e.getMessage()).doesNotContain("lógica"));
    }
}
