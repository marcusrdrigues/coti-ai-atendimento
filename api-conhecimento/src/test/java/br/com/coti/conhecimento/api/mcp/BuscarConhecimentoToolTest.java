package br.com.coti.conhecimento.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.aplicacao.BuscarConhecimentoService;
import br.com.coti.conhecimento.aplicacao.porta.BuscaVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.LimitesBusca;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import tools.jackson.databind.json.JsonMapper;

/** A ferramenta só traduz o protocolo: delega ao caso de uso e converte falhas em {@link ErroFerramentaException}. */
class BuscarConhecimentoToolTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);

    private Function<BuscaVetorialPort.Consulta, List<TrechoEncontrado>> comportamento = consulta -> List.of();
    private BuscaVetorialPort.Consulta ultimaConsulta;

    private final BuscarConhecimentoService servico = new BuscarConhecimentoService(consulta -> {
        ultimaConsulta = consulta;
        return comportamento.apply(consulta);
    }, new ModeloEmbedding("text-embedding-3-small"), LimitesBusca.padrao(), RELOGIO);

    private final BuscarConhecimentoTool ferramenta = new BuscarConhecimentoTool(servico);
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void fechar() {
        servico.close();
    }

    private ErroFerramenta erroDe(String pergunta, Integer limite) {
        return catchErro(() -> ferramenta.buscar(pergunta, limite)).erro();
    }

    private static ErroFerramentaException catchErro(Runnable acao) {
        try {
            acao.run();
        } catch (ErroFerramentaException e) {
            return e;
        }
        throw new AssertionError("era esperada uma ErroFerramentaException");
    }

    @Test
    void delegaAoCasoDeUsoEConverteOResultadoParaOContrato() {
        UUID id = UUID.randomUUID();
        UUID documentoId = UUID.randomUUID();
        comportamento = consulta -> List.of(new TrechoEncontrado(id, "Texto do trecho", documentoId, "Título",
                "Fonte", Categoria.MODALIDADE, 0.83));

        ResultadoBusca resultado = ferramenta.buscar("  As aulas são online?  ", 3);

        assertThat(ultimaConsulta.pergunta()).isEqualTo("As aulas são online?");
        assertThat(ultimaConsulta.limite()).isEqualTo(3);
        assertThat(resultado.total()).isEqualTo(1);
        assertThat(resultado.trechos()).containsExactly(new TrechoResultado(id, "Texto do trecho", documentoId,
                "Título", "Fonte", Categoria.MODALIDADE, 0.83));
    }

    @Test
    void semLimiteUsaOPadraoDoCasoDeUso() {
        ferramenta.buscar("pergunta válida", null);

        assertThat(ultimaConsulta.limite()).isEqualTo(5);
    }

    @Test
    void listaVaziaEhSucesso() {
        ResultadoBusca resultado = ferramenta.buscar("pergunta válida", null);

        assertThat(resultado.trechos()).isEmpty();
        assertThat(resultado.total()).isZero();
    }

    @Test
    void argumentosInvalidosViramArgumentosInvalidosComMensagemDoCampo() {
        assertThat(erroDe("ab", null)).isEqualTo(new ErroFerramenta("ARGUMENTOS_INVALIDOS",
                "O campo 'pergunta' deve ter entre 3 e 1.000 caracteres."));
        assertThat(erroDe("pergunta válida", 11)).isEqualTo(new ErroFerramenta("ARGUMENTOS_INVALIDOS",
                "O campo 'limite' deve estar entre 1 e 10."));
        assertThat(erroDe(null, null).codigo()).isEqualTo("ARGUMENTOS_INVALIDOS");
    }

    @Test
    void falhaDoBancoViraBaseConhecimentoIndisponivelSemOsDetalhes() {
        comportamento = consulta -> {
            throw new BaseConhecimentoIndisponivelException(new IllegalStateException("jdbc:postgresql://interno/segredo"));
        };

        ErroFerramenta erro = erroDe("pergunta válida", null);

        assertThat(erro.codigo()).isEqualTo("BASE_CONHECIMENTO_INDISPONIVEL");
        assertThat(erro.mensagem()).doesNotContain("jdbc", "segredo");
    }

    @Test
    void falhaDoEmbeddingViraModeloIndisponivel() {
        comportamento = consulta -> {
            throw new ModeloIndisponivelException(new RuntimeException("429 do provedor"));
        };

        ErroFerramenta erro = erroDe("pergunta válida", null);

        assertThat(erro.codigo()).isEqualTo("MODELO_INDISPONIVEL");
        assertThat(erro.mensagem()).doesNotContain("429");
    }

    @Test
    void qualquerOutraFalhaViraErroInternoSemRevelarDetalhe() {
        comportamento = consulta -> {
            throw new IllegalStateException("NullPointerException em br.com.coti.Interna");
        };

        ErroFerramenta erro = erroDe("pergunta válida", null);

        assertThat(erro.codigo()).isEqualTo("ERRO_INTERNO");
        assertThat(erro.mensagem()).isEqualTo("Ocorreu um erro inesperado ao buscar conhecimento.");
    }

    @Test
    void aMensagemDaExcecaoEOJsonDoErroEACausaTemMensagemVazia() {
        ErroFerramentaException excecao = catchErro(() -> ferramenta.buscar("ab", null));

        assertThat(json.readTree(excecao.getMessage()).get("codigo").asString()).isEqualTo("ARGUMENTOS_INVALIDOS");
        // O Spring AI 2.0.1 monta o texto como mensagem + quebra de linha + mensagem da causa raiz. A causa vazia
        // evita que o JSON saia duplicado.
        assertThat(excecao.getCause()).isNotNull();
        assertThat(excecao.getCause().getMessage()).isEmpty();
        String textoDoSpringAi = excecao.getMessage() + System.lineSeparator() + excecao.getCause().getMessage();
        assertThat(json.readTree(textoDoSpringAi.strip()).get("codigo").asString()).isEqualTo("ARGUMENTOS_INVALIDOS");
    }

    @Test
    void aExcecaoNaoEChecada() {
        assertThat(RuntimeException.class).isAssignableFrom(ErroFerramentaException.class);
        assertThatThrownBy(() -> ferramenta.buscar(null, null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void oMetodoDeclaraAFerramentaSomenteLeituraDoContrato() throws NoSuchMethodException {
        Method metodo = BuscarConhecimentoTool.class.getMethod("buscar", String.class, Integer.class);
        McpTool anotacao = metodo.getAnnotation(McpTool.class);

        assertThat(anotacao.name()).isEqualTo("buscar_conhecimento");
        assertThat(anotacao.title()).isEqualTo("Buscar conhecimento COTI");
        assertThat(anotacao.generateOutputSchema()).isTrue();
        assertThat(anotacao.annotations().readOnlyHint()).isTrue();
        assertThat(anotacao.annotations().destructiveHint()).isFalse();
        assertThat(anotacao.annotations().idempotentHint()).isTrue();
        assertThat(anotacao.annotations().openWorldHint()).isFalse();
        assertThat(metodo.getExceptionTypes()).as("sem exceção checada: o Spring AI a trataria como falha dura")
                .isEmpty();
    }
}
