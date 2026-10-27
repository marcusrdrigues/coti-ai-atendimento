package br.com.coti.atendimentos.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.Fonte;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DerivadorFontesTest {

    private final DerivadorFontes derivador = new DerivadorFontes();

    private static TrechoConhecimento trecho(String titulo, double similaridade) {
        return new TrechoConhecimento(UUID.randomUUID(), "Texto de " + titulo + ".", UUID.randomUUID(), titulo,
                "Site COTI", Categoria.CURSO, similaridade);
    }

    private static ContextoRag contexto(TrechoConhecimento... trechos) {
        return new MontadorContextoRag(MontadorContextoRag.Limites.padrao()).montar(List.of(trechos));
    }

    @Test
    void copiaTodosOsCamposDoTrechoDoMcp() {
        TrechoConhecimento t = trecho("Java", 0.91);

        assertThat(derivador.derivar(List.of("T1"), contexto(t))).containsExactly(Fonte.de(t));
    }

    @Test
    void soAsCitadasEntramNaOrdemDoContextoNaoDaListaDoModelo() {
        TrechoConhecimento a = trecho("A", 0.95);
        TrechoConhecimento b = trecho("B", 0.90);
        TrechoConhecimento c = trecho("C", 0.85);

        assertThat(derivador.derivar(List.of("T3", "T1"), contexto(c, b, a))).containsExactly(Fonte.de(a), Fonte.de(c));
    }

    @Test
    void referenciaRepetidaGeraUmaFonteSo() {
        assertThat(derivador.derivar(List.of("T1", "T1", "T1"), contexto(trecho("A", 0.9)))).hasSize(1);
    }

    @Test
    void doisTrechosDoMesmoDocumentoSaoDuasFontesPoisOIdDoTrechoDifere() {
        TrechoConhecimento a = trecho("Mesmo documento", 0.95);
        TrechoConhecimento b = new TrechoConhecimento(UUID.randomUUID(), "Outro pedaço.", a.documentoId(),
                a.titulo(), a.fonte(), a.categoria(), 0.90);

        assertThat(derivador.derivar(List.of("T1", "T2"), contexto(a, b))).hasSize(2);
    }

    @Test
    void semReferenciasNaoHaFontes() {
        assertThat(derivador.derivar(List.of(), contexto(trecho("A", 0.9)))).isEmpty();
    }

    @Test
    void referenciaDesconhecidaNaoGeraFonte() {
        assertThat(derivador.derivar(List.of("T9", "texto livre"), contexto(trecho("A", 0.9)))).isEmpty();
    }
}
