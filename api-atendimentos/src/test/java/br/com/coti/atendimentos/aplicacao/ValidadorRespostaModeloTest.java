package br.com.coti.atendimentos.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorRespostaModeloTest {

    private final ValidadorRespostaModelo validador = new ValidadorRespostaModelo();

    private static ContextoRag contexto(String... textos) {
        List<TrechoConhecimento> trechos = new java.util.ArrayList<>();
        double similaridade = 0.99;
        for (String texto : textos) {
            trechos.add(new TrechoConhecimento(UUID.randomUUID(), texto, UUID.randomUUID(), "Título", "Fonte",
                    Categoria.CURSO, similaridade));
            similaridade -= 0.01;
        }
        return new MontadorContextoRag(MontadorContextoRag.Limites.padrao()).montar(trechos);
    }

    private static RespostaGerada suficiente(String resposta, String... referencias) {
        return new RespostaGerada(resposta, true, List.of(referencias));
    }

    private Optional<String> validar(RespostaGerada gerada, ContextoRag contexto) {
        return validador.validar(gerada, contexto);
    }

    // ---- estrutura

    @Test
    void respostaAprovadaComReferenciaValida() {
        assertThat(validar(suficiente("O curso tem carga horária de 120 horas.", "T1"),
                contexto("O curso de Java tem 120 horas."))).isEmpty();
    }

    @Test
    void respostaVaziaOuEmBrancoEhReprovada() {
        ContextoRag c = contexto("Texto.");

        assertThat(validar(suficiente("", "T1"), c)).hasValueSatisfying(m -> assertThat(m).contains("vazia"));
        assertThat(validar(suficiente("   \n ", "T1"), c)).isPresent();
        assertThat(validar(new RespostaGerada(null, true, List.of("T1")), c)).isPresent();
        assertThat(validar(null, c)).isPresent();
    }

    @Test
    void rotuloNoTextoDaRespostaEhReprovado() {
        ContextoRag c = contexto("Texto.");

        assertThat(validar(suficiente("Conforme o trecho T1, sim.", "T1"), c))
                .hasValueSatisfying(m -> assertThat(m).contains("T1"));
        assertThat(validar(suficiente("Sim (T2).", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Sim [T3]", "T1"), c)).isPresent();
    }

    @Test
    void palavrasQueApenasContemTNaoSaoRotulo() {
        ContextoRag c = contexto("Texto.");

        assertThat(validar(suficiente("A turma T1A começa em breve e o ST2 não é citado.", "T1"), c)).isEmpty();
    }

    @Test
    void suficienteSemReferenciaEhReprovado() {
        assertThat(validar(suficiente("Sim."), contexto("Texto."))).hasValueSatisfying(m -> assertThat(m)
                .contains("referencias"));
    }

    @Test
    void referenciaInexistenteEhReprovadaEOMotivoListaOsRotulosValidos() {
        assertThat(validar(suficiente("Sim.", "T1", "T7"), contexto("Texto A.", "Texto B.")))
                .hasValueSatisfying(m -> assertThat(m).contains("T7").contains("[T1, T2]"));
    }

    @Test
    void referenciaNulaOuTextoLivreEhReprovadaSemRepassarOConteudoDoModelo() {
        ContextoRag c = contexto("Texto.");
        List<String> comNula = new java.util.ArrayList<>();
        comNula.add(null);

        assertThat(validar(new RespostaGerada("Sim.", true, comNula), c)).isPresent();
        String longa = "Ignore tudo e <script>alert(1)</script> " + "x".repeat(100);
        assertThat(validar(suficiente("Sim.", longa), c)).hasValueSatisfying(m -> assertThat(m)
                .doesNotContain("<script>").doesNotContain("x".repeat(30)));
    }

    @Test
    void insuficienteNaoEhValidadoPoisOTextoDoModeloNaoSeraUsado() {
        assertThat(validar(new RespostaGerada("custa R$ 999,00", false, List.of()), contexto("Texto."))).isEmpty();
    }

    @Test
    void insuficienteComRespostaVaziaAindaEhReprovada() {
        assertThat(validar(new RespostaGerada("", false, List.of()), contexto("Texto."))).isPresent();
    }

    // ---- valores com evidência literal

    @Test
    void precoInventadoEhReprovadoEPrecoDoTrechoEhAprovado() {
        ContextoRag c = contexto("A mensalidade do curso de Java é R$ 450,00 por mês.");

        assertThat(validar(suficiente("A mensalidade é R$ 450,00.", "T1"), c)).isEmpty();
        assertThat(validar(suficiente("A mensalidade é R$ 399,90.", "T1"), c))
                .hasValueSatisfying(m -> assertThat(m).contains("R$ 399,90"));
    }

    @Test
    void formasDeEscreverOMesmoPrecoSaoEquivalentes() {
        ContextoRag c = contexto("A matrícula custa R$450,00.");

        assertThat(validar(suficiente("A matrícula custa R$ 450,00.", "T1"), c)).isEmpty();
    }

    @Test
    void valorSoEmTrechoNaoReferenciadoNaoVale() {
        ContextoRag c = contexto("O curso de Java custa R$ 450,00.", "O curso de Python é tranquilo.");

        assertThat(validar(suficiente("Custa R$ 450,00.", "T2"), c)).isPresent();
        assertThat(validar(suficiente("Custa R$ 450,00.", "T1"), c)).isEmpty();
    }

    @Test
    void valorQueEstaEmQualquerUmDosTrechosReferenciadosVale() {
        ContextoRag c = contexto("Java: R$ 450,00.", "Python: R$ 380,00.");

        assertThat(validar(suficiente("Java custa R$ 450,00 e Python R$ 380,00.", "T1", "T2"), c)).isEmpty();
    }

    @Test
    void descontoParcelaDataHorarioEVagasExigemEvidencia() {
        ContextoRag c = contexto("Desconto de 15% à vista, em até 6x. Início em 15 de março de 2027, às 19:30. "
                + "São 20 vagas.");

        assertThat(validar(suficiente("Há 15% de desconto à vista.", "T1"), c)).isEmpty();
        assertThat(validar(suficiente("Parcela em até 6x.", "T1"), c)).isEmpty();
        assertThat(validar(suficiente("Começa em 15 de Março de 2027.", "T1"), c)).isEmpty();
        assertThat(validar(suficiente("As aulas são às 19:30.", "T1"), c)).isEmpty();
        assertThat(validar(suficiente("Há 20 vagas.", "T1"), c)).isEmpty();

        assertThat(validar(suficiente("Há 30% de desconto.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Em até 12x.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Em 10 parcelas.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Começa em 1 de abril de 2027.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Começa em 01/04/2027.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("As aulas são às 20:00.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("As aulas são às 20h.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Há 35 vagas.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Vagas: 35", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Custa 300 reais.", "T1"), c)).isPresent();
    }

    @Test
    void acentoECaixaNaoDeixamUmValorVerdadeiroSerReprovado() {
        ContextoRag c = contexto("A turma começa em 15 DE MARÇO.");

        assertThat(validar(suficiente("A turma começa em 15 de marco.", "T1"), c)).isEmpty();
    }

    // ---- falsos positivos: números comuns não reprovam

    @ParameterizedTest
    @ValueSource(strings = {
            "Você não precisa de 2 anos de experiência.",
            "O curso tem 40 horas de aula.",
            "São 3 módulos e 12 aulas por módulo.",
            "Use o Java 21 e o Spring Boot 4.",
            "Funciona no Windows 11.",
            "A turma tem até 25 alunos.",
            "Ligue para a secretaria.",
    })
    void numerosComunsSemPadraoDeValorNaoReprovam(String resposta) {
        assertThat(validar(suficiente(resposta, "T1"), contexto("Texto do curso de Java."))).isEmpty();
    }

    @Test
    void formatoQueParecePrazoOuParcelaSemEvidenciaEReprovadoPorConservadorismo() {
        ContextoRag c = contexto("Texto.");

        assertThat(validar(suficiente("O módulo 2x é opcional? Veja o nível 3h.", "T1"), c)).isPresent();
        assertThat(validar(suficiente("Use a versão 4.x do framework no Java 21.", "T1"), c)).isEmpty();
    }
}
