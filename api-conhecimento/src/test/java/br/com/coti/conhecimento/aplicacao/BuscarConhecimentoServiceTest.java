package br.com.coti.conhecimento.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.aplicacao.porta.BuscaVetorialPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.LimitesBusca;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.TrechoEncontrado;
import br.com.coti.conhecimento.dominio.excecao.ArgumentosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.BaseConhecimentoIndisponivelException;
import br.com.coti.conhecimento.dominio.excecao.ModeloIndisponivelException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BuscarConhecimentoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);

    private final List<BuscaVetorialPort.Consulta> consultas = new ArrayList<>();
    private List<TrechoEncontrado> respostaDoBanco = List.of();
    private BuscarConhecimentoService servico = criar(LimitesBusca.padrao(), consulta -> {
        consultas.add(consulta);
        return respostaDoBanco;
    });

    @AfterEach
    void fechar() {
        servico.close();
    }

    private static BuscarConhecimentoService criar(LimitesBusca limites, BuscaVetorialPort porta) {
        return new BuscarConhecimentoService(porta, new ModeloEmbedding("text-embedding-3-small"), limites, RELOGIO);
    }

    private static TrechoEncontrado trecho(String texto, Double similaridade) {
        return new TrechoEncontrado(UUID.randomUUID(), texto, UUID.randomUUID(), "Título", "Fonte", Categoria.CURSO,
                similaridade);
    }

    private static String caracteres(int quantidade) {
        return "a".repeat(quantidade);
    }

    // ---- validação

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "  ab  ", "a\n"})
    void perguntaCurtaOuVaziaEhRecusadaSemChamarABusca(String pergunta) {
        assertThatThrownBy(() -> servico.buscar(pergunta, null))
                .isInstanceOfSatisfying(ArgumentosInvalidosException.class, e -> {
                    assertThat(e.campo()).isEqualTo("pergunta");
                    assertThat(e.getMessage()).isEqualTo("O campo 'pergunta' deve ter entre 3 e 1.000 caracteres.");
                });
        assertThat(consultas).isEmpty();
    }

    @Test
    void perguntaComMaisDeMilCaracteresEhRecusadaSemEcoDoValor() {
        String longa = caracteres(1_001);

        assertThatThrownBy(() -> servico.buscar(longa, null))
                .isInstanceOf(ArgumentosInvalidosException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(longa));
        assertThat(consultas).isEmpty();
    }

    @Test
    void perguntaNosLimitesDe3E1000CaracteresEhAceita() {
        servico.buscar("abc", null);
        servico.buscar(caracteres(1_000), null);

        assertThat(consultas).hasSize(2);
    }

    @Test
    void espacosNasPontasSaoRemovidosAntesDeValidarEDeBuscar() {
        servico.buscar("   Qual o valor do curso?  \n", null);

        assertThat(consultas.get(0).pergunta()).isEqualTo("Qual o valor do curso?");
    }

    @Test
    void perguntaQueFicaCurtaDepoisDeRemoverEspacosEhRecusada() {
        assertThatThrownBy(() -> servico.buscar("  ab  ", null)).isInstanceOf(ArgumentosInvalidosException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void limiteForaDeUmADezEhRecusado(int limite) {
        assertThatThrownBy(() -> servico.buscar("pergunta válida", limite))
                .isInstanceOfSatisfying(ArgumentosInvalidosException.class, e -> {
                    assertThat(e.campo()).isEqualTo("limite");
                    assertThat(e.getMessage()).isEqualTo("O campo 'limite' deve estar entre 1 e 10.");
                });
        assertThat(consultas).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10})
    void limiteDeUmADezEhRepassadoABusca(int limite) {
        servico.buscar("pergunta válida", limite);

        assertThat(consultas.get(0).limite()).isEqualTo(limite);
    }

    @Test
    void semLimiteUsaOPadraoDeCinco() {
        servico.buscar("pergunta válida", null);

        assertThat(consultas.get(0).limite()).isEqualTo(5);
    }

    // ---- consulta enviada à base

    @Test
    void aBuscaRecebeModeloLimiarEDataDeHoje() {
        servico.buscar("pergunta válida", 3);

        BuscaVetorialPort.Consulta consulta = consultas.get(0);
        assertThat(consulta.modeloEmbedding()).isEqualTo("text-embedding-3-small");
        assertThat(consulta.limiarSimilaridade()).isEqualTo(0.70);
        assertThat(consulta.hoje()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void aDescricaoDaConsultaNaoExpoeAPergunta() {
        servico.buscar("pergunta confidencial do aluno", null);

        assertThat(consultas.get(0).toString()).doesNotContain("confidencial");
    }

    // ---- ordem e cortes

    @Test
    void ordenaPorSimilaridadeDecrescente() {
        respostaDoBanco = List.of(trecho("b", 0.75), trecho("a", 0.91), trecho("c", 0.80));

        ResultadoDaBusca resultado = servico.buscar("pergunta válida", null);

        assertThat(resultado.trechos()).extracting(TrechoEncontrado::texto).containsExactly("a", "c", "b");
        assertThat(resultado.total()).isEqualTo(3);
    }

    @Test
    void semScoreMantemAOrdemDaBuscaEColocaOsSemScoreNoFim() {
        respostaDoBanco = List.of(trecho("sem1", null), trecho("com", 0.80), trecho("sem2", null));

        ResultadoDaBusca resultado = servico.buscar("pergunta válida", null);

        assertThat(resultado.trechos()).extracting(TrechoEncontrado::texto).containsExactly("com", "sem1", "sem2");
    }

    @Test
    void cadaTextoEhCortadoEm2000Caracteres() {
        respostaDoBanco = List.of(trecho(caracteres(2_500), 0.9));

        ResultadoDaBusca resultado = servico.buscar("pergunta válida", null);

        assertThat(resultado.trechos().get(0).texto()).hasSize(2_000);
    }

    @Test
    void textoNoLimiteDe2000CaracteresNaoEhAlterado() {
        respostaDoBanco = List.of(trecho(caracteres(2_000), 0.9));

        assertThat(servico.buscar("pergunta válida", null).trechos().get(0).texto()).hasSize(2_000);
    }

    @Test
    void cincoTrechosQueSomam12MilCaracteresViramNoMaximo8MilMantendoOsDeMaiorSimilaridade() {
        respostaDoBanco = List.of(trecho(caracteres(2_400), 0.70), trecho(caracteres(2_400), 0.95),
                trecho(caracteres(2_400), 0.80), trecho(caracteres(2_400), 0.90), trecho(caracteres(2_400), 0.85));

        ResultadoDaBusca resultado = servico.buscar("pergunta válida", null);

        assertThat(resultado.trechos()).extracting(TrechoEncontrado::similaridade).containsExactly(0.95, 0.90, 0.85, 0.80);
        assertThat(resultado.trechos().stream().mapToInt(t -> t.texto().length()).sum()).isLessThanOrEqualTo(8_000);
    }

    @Test
    void trechoQueFariaPassarDe8MilSaiInteiroENaoPelaMetade() {
        respostaDoBanco = List.of(trecho(caracteres(1_500), 0.95), trecho(caracteres(1_500), 0.90),
                trecho(caracteres(1_500), 0.85), trecho(caracteres(1_500), 0.80), trecho(caracteres(1_500), 0.75),
                trecho(caracteres(1_500), 0.72));

        ResultadoDaBusca resultado = servico.buscar("pergunta válida", 10);

        assertThat(resultado.trechos()).hasSize(5).allSatisfy(t -> assertThat(t.texto()).hasSize(1_500));
        assertThat(resultado.trechos()).extracting(TrechoEncontrado::similaridade)
                .doesNotContain(0.72);
    }

    @Test
    void somaExatamenteIgualA8MilCabe() {
        respostaDoBanco = List.of(trecho(caracteres(2_000), 0.9), trecho(caracteres(2_000), 0.8),
                trecho(caracteres(2_000), 0.7), trecho(caracteres(2_000), 0.6));

        assertThat(servico.buscar("pergunta válida", 10).trechos()).hasSize(4);
    }

    // ---- resultado

    @Test
    void listaVaziaEhSucesso() {
        respostaDoBanco = List.of();

        ResultadoDaBusca resultado = servico.buscar("qual a previsão do tempo amanhã?", null);

        assertThat(resultado.trechos()).isEmpty();
        assertThat(resultado.total()).isZero();
    }

    @Test
    void oResultadoTrazLimiarModeloEDuracao() {
        ResultadoDaBusca resultado = servico.buscar("pergunta válida", null);

        assertThat(resultado.limiarSimilaridade()).isEqualTo(0.70);
        assertThat(resultado.modeloEmbedding()).isEqualTo("text-embedding-3-small");
        assertThat(resultado.duracao()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    // ---- falhas

    @Test
    void buscaMaisLentaQueOTempoLimiteViraModeloIndisponivelEInterrompeATarefa() throws Exception {
        AtomicBoolean interrompida = new AtomicBoolean();
        AtomicReference<Thread> tarefa = new AtomicReference<>();
        LimitesBusca limites = new LimitesBusca(3, 1_000, 5, 10, 0.70, 2_000, 8_000, Duration.ofMillis(200));
        try (BuscarConhecimentoService lento = criar(limites, consulta -> {
            tarefa.set(Thread.currentThread());
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                interrompida.set(true);
            }
            return List.of();
        })) {
            long inicio = System.nanoTime();

            assertThatThrownBy(() -> lento.buscar("pergunta válida", null)).isInstanceOf(ModeloIndisponivelException.class);

            assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
            tarefa.get().join(Duration.ofSeconds(2));
            assertThat(interrompida).isTrue();
        }
    }

    @Test
    void falhasDaPortaSaoPropagadasSemTraducaoAdicional() {
        try (BuscarConhecimentoService quebrado = criar(LimitesBusca.padrao(), consulta -> {
            throw new BaseConhecimentoIndisponivelException(new RuntimeException("conexão recusada"));
        })) {
            assertThatThrownBy(() -> quebrado.buscar("pergunta válida", null))
                    .isInstanceOf(BaseConhecimentoIndisponivelException.class);
        }
    }

    @Test
    void falhaInesperadaDaPortaChegaComoRuntimeExceptionOriginal() {
        try (BuscarConhecimentoService quebrado = criar(LimitesBusca.padrao(), consulta -> {
            throw new IllegalStateException("metadado ausente");
        })) {
            assertThatThrownBy(() -> quebrado.buscar("pergunta válida", null))
                    .isInstanceOf(IllegalStateException.class).hasMessage("metadado ausente");
        }
    }

    // ---- configuração

    @Test
    void limitesIncoerentesSaoRecusadosNaCriacao() {
        assertThatThrownBy(() -> new LimitesBusca(3, 2, 5, 10, 0.7, 2_000, 8_000, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesBusca(3, 1_000, 11, 10, 0.7, 2_000, 8_000, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesBusca(3, 1_000, 5, 10, 1.5, 2_000, 8_000, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesBusca(3, 1_000, 5, 10, 0.7, 9_000, 8_000, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesBusca(3, 1_000, 5, 10, 0.7, 2_000, 8_000, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
