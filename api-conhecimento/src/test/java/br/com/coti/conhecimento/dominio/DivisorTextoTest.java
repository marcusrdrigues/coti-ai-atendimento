package br.com.coti.conhecimento.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.conhecimento.dominio.excecao.DocumentoGrandeDemaisException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DivisorTextoTest {

    private static final UUID DOC = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static DivisorTexto divisor(int max, int sobreposicao, int min, int trechos) {
        return new DivisorTexto(new ConfiguracaoDivisao(max, sobreposicao, min, trechos));
    }

    private static DivisorTexto padrao() {
        return new DivisorTexto(ConfiguracaoDivisao.padrao());
    }

    /** Texto com frases numeradas, para saber exatamente onde cada trecho começa e termina. */
    private static String frases(int quantidade) {
        return IntStream.rangeClosed(1, quantidade)
                .mapToObj(i -> "Esta é a frase número " + i + " do documento de teste.")
                .collect(Collectors.joining(" "));
    }

    @Test
    void textoCurtoViraUmUnicoTrechoComOTextoInteiro() {
        String texto = "O curso de Angular tem duas modalidades.";

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos).singleElement().satisfies(t -> {
            assertThat(t.texto()).isEqualTo(texto);
            assertThat(t.ordem()).isZero();
        });
    }

    @Test
    void textoExatamenteNoTamanhoMaximoNaoEDividido() {
        String texto = "a ".repeat(749) + "a"; // 1.499 caracteres

        assertThat(padrao().dividir(DOC, texto)).hasSize(1);
        assertThat(padrao().dividir(DOC, "x".repeat(1500))).hasSize(1);
        assertThat(padrao().dividir(DOC, "x".repeat(1501))).hasSizeGreaterThan(1);
    }

    @Test
    void textoVazioOuEmBrancoNaoGeraTrechos() {
        assertThat(padrao().dividir(DOC, "")).isEmpty();
        assertThat(padrao().dividir(DOC, "   \n\n  ")).isEmpty();
        assertThat(padrao().dividir(DOC, null)).isEmpty();
    }

    @Test
    void nenhumTrechoPassaDoTamanhoMaximo() {
        List<Trecho> trechos = padrao().dividir(DOC, frases(300));

        assertThat(trechos).hasSizeGreaterThan(3);
        assertThat(trechos).allSatisfy(t -> assertThat(t.texto().length()).isLessThanOrEqualTo(1500));
    }

    @Test
    void trechosVizinhosCompartilhamAteASobreposicaoConfigurada() {
        String texto = frases(300);
        List<Trecho> trechos = padrao().dividir(DOC, texto);

        for (int i = 1; i < trechos.size(); i++) {
            String anterior = trechos.get(i - 1).texto();
            String atual = trechos.get(i).texto();
            int comum = maiorSufixoQueEPrefixo(anterior, atual);
            assertThat(comum).as("sobreposição entre %d e %d", i - 1, i).isPositive().isLessThanOrEqualTo(200);
        }
    }

    @Test
    void sobreposicaoComecaNoInicioDeUmaPalavra() {
        List<Trecho> trechos = padrao().dividir(DOC, frases(300));

        for (int i = 1; i < trechos.size(); i++) {
            String primeiraPalavra = trechos.get(i).texto().split(" ")[0];
            // a palavra inicial aparece inteira, entre espaços, no fim do trecho anterior
            assertThat(trechos.get(i - 1).texto()).contains(" " + primeiraPalavra + " ");
        }
    }

    @Test
    void naoPerdeNenhumaPalavraDoDocumento() {
        String texto = frases(300);
        List<Trecho> trechos = padrao().dividir(DOC, texto);

        String uniao = trechos.stream().map(Trecho::texto).collect(Collectors.joining(" "));
        for (int i = 1; i <= 300; i++) {
            assertThat(uniao).contains("frase número " + i + " ");
        }
        assertThat(trechos.get(trechos.size() - 1).texto()).endsWith("frase número 300 do documento de teste.");
        assertThat(trechos.get(0).texto()).startsWith("Esta é a frase número 1 ");
    }

    @Test
    void cortaEmFimDeFraseQuandoHaPontoNaJanela() {
        List<Trecho> trechos = padrao().dividir(DOC, frases(300));

        assertThat(trechos.subList(0, trechos.size() - 1))
                .allSatisfy(t -> assertThat(t.texto()).endsWith("."));
    }

    @Test
    void preferePararNoFimDoParagrafoAoFimDeFrase() {
        String paragrafo1 = frases(18); // ~ 900 caracteres
        String paragrafo2 = frases(40);
        String texto = paragrafo1 + "\n\n" + paragrafo2;

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos.get(0).texto()).isEqualTo(paragrafo1);
    }

    @Test
    void cortaEmQuebraDeLinhaQuandoNaoHaFrase() {
        String linha = "item de lista sem pontuacao final ".repeat(5).strip();
        String texto = (linha + "\n").repeat(30).strip();

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos).hasSizeGreaterThan(1);
        assertThat(trechos.get(0).texto()).endsWith("final");
        assertThat(trechos.get(0).texto().length()).isLessThanOrEqualTo(1500);
    }

    @Test
    void cortaEmEspacoQuandoNaoHaFraseNemLinha() {
        String texto = "palavra ".repeat(600).strip();

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos).hasSizeGreaterThan(1);
        assertThat(trechos).allSatisfy(t -> assertThat(t.texto()).startsWith("palavra").endsWith("palavra"));
    }

    @Test
    void textoSemEspacosNaoEntraEmLacoENaoTemSobreposicao() {
        String texto = "x".repeat(5000);

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos).hasSize(4);
        assertThat(trechos.stream().mapToInt(t -> t.texto().length()).sum()).isEqualTo(5000);
        assertThat(trechos).allSatisfy(t -> assertThat(t.texto().length()).isLessThanOrEqualTo(1500));
    }

    @Test
    void mesmaEntradaEMesmaConfiguracaoGeramOsMesmosTrechosEIds() {
        String texto = frases(200);

        assertThat(padrao().dividir(DOC, texto)).isEqualTo(padrao().dividir(DOC, texto));
    }

    @Test
    void idsDependemDoDocumentoEDaOrdem() {
        String texto = frases(200);
        List<Trecho> a = padrao().dividir(DOC, texto);
        List<Trecho> b = padrao().dividir(UUID.randomUUID(), texto);

        assertThat(a).extracting(Trecho::id).doesNotHaveDuplicates();
        assertThat(a).extracting(Trecho::ordem).containsExactlyElementsOf(IntStream.range(0, a.size()).boxed().toList());
        assertThat(a.get(0).id()).isNotEqualTo(b.get(0).id());
        assertThat(a.get(0).id()).isEqualTo(IdTrecho.de(DOC, 0));
    }

    @Test
    void recusaDocumentoQueGeraMaisTrechosQueOMaximo() {
        DivisorTexto pequeno = divisor(100, 20, 10, 5);

        assertThatThrownBy(() -> pequeno.dividir(DOC, frases(100)))
                .isInstanceOfSatisfying(DocumentoGrandeDemaisException.class,
                        ex -> assertThat(ex.maximoTrechos()).isEqualTo(5));
    }

    @Test
    void aceitaDocumentoComExatamenteOMaximoDeTrechos() {
        DivisorTexto pequeno = divisor(100, 0, 10, 200);
        int total = pequeno.dividir(DOC, frases(100)).size();

        assertThat(divisor(100, 0, 10, total).dividir(DOC, frases(100))).hasSize(total);
        assertThatThrownBy(() -> divisor(100, 0, 10, total - 1).dividir(DOC, frases(100)))
                .isInstanceOf(DocumentoGrandeDemaisException.class);
    }

    @Test
    void semSobreposicaoOsTrechosNaoSeRepetem() {
        String texto = frases(100);
        List<Trecho> trechos = divisor(300, 0, 50, 500).dividir(DOC, texto);

        for (int i = 1; i < trechos.size(); i++) {
            assertThat(maiorSufixoQueEPrefixo(trechos.get(i - 1).texto(), trechos.get(i).texto())).isZero();
        }
    }

    @ParameterizedTest
    @CsvSource({"0,10,1,1", "100,100,10,1", "100,150,10,1", "100,-1,10,1", "100,20,0,1", "100,20,101,1", "100,20,10,0"})
    void configuracaoInvalidaEhRecusada(int max, int sobreposicao, int min, int trechos) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConfiguracaoDivisao(max, sobreposicao, min, trechos));
    }

    @Test
    void configuracaoPadraoEhADaEspecificacao() {
        ConfiguracaoDivisao padrao = ConfiguracaoDivisao.padrao();

        assertThat(padrao.tamanhoMaximo()).isEqualTo(1500);
        assertThat(padrao.sobreposicao()).isEqualTo(200);
        assertThat(padrao.tamanhoMinimo()).isEqualTo(50);
        assertThat(padrao.maximoTrechos()).isEqualTo(500);
    }

    @Test
    void textoGrandeDeVerdadeTerminaRapidoESemRepeticaoInfinita() {
        String texto = frases(2000); // ~ 100 mil caracteres, o máximo do cadastro

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos.size()).isBetween(60, 120);
        assertThat(trechos).allSatisfy(t -> assertThat(t.texto().length()).isBetween(50, 1500));
    }

    @Test
    void ultimoTrechoCurtoNaoEDescartado() {
        // 3 frases longas e uma curta no fim: o fim do documento tem que sobreviver
        String texto = frases(40) + " Matrículas até 30/06.";

        List<Trecho> trechos = padrao().dividir(DOC, texto);

        assertThat(trechos.get(trechos.size() - 1).texto()).endsWith("Matrículas até 30/06.");
    }

    private static int maiorSufixoQueEPrefixo(String anterior, String atual) {
        int maximo = Math.min(anterior.length(), atual.length());
        for (int tamanho = maximo; tamanho > 0; tamanho--) {
            if (anterior.endsWith(atual.substring(0, tamanho))) {
                return tamanho;
            }
        }
        return 0;
    }
}
