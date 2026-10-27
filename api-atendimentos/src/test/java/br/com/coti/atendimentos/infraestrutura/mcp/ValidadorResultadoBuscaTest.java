package br.com.coti.atendimentos.infraestrutura.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.atendimentos.dominio.Categoria;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.BaseConhecimentoIndisponivelException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorResultadoBuscaTest {

    private final ValidadorResultadoBusca validador = new ValidadorResultadoBusca();

    private static Map<String, Object> trecho() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("id", UUID.randomUUID().toString());
        t.put("texto", "Para o curso Java é recomendado conhecimento básico de lógica.");
        t.put("documentoId", UUID.randomUUID().toString());
        t.put("titulo", "Curso Java Fullstack — pré-requisitos");
        t.put("fonte", "Site COTI, out/2026");
        t.put("categoria", "PRE_REQUISITO");
        t.put("similaridade", 0.86);
        return t;
    }

    private static Map<String, Object> trecho(Consumer<Map<String, Object>> ajuste) {
        Map<String, Object> t = trecho();
        ajuste.accept(t);
        return t;
    }

    private static CallToolResult resultado(List<Map<String, Object>> trechos) {
        Map<String, Object> estruturado = new LinkedHashMap<>();
        estruturado.put("trechos", trechos);
        estruturado.put("total", trechos.size());
        return new CallToolResult(List.of(new TextContent("{}")), false, estruturado, null);
    }

    private static CallToolResult resultado(Map<String, Object> estruturado) {
        return new CallToolResult(List.of(new TextContent("{}")), false, estruturado, null);
    }

    @Test
    void converteUmResultadoValidoParaODominio() {
        Map<String, Object> bruto = trecho();

        List<TrechoConhecimento> trechos = validador.validar(resultado(List.of(bruto)));

        assertThat(trechos).containsExactly(new TrechoConhecimento(UUID.fromString((String) bruto.get("id")),
                (String) bruto.get("texto"), UUID.fromString((String) bruto.get("documentoId")),
                (String) bruto.get("titulo"), (String) bruto.get("fonte"), Categoria.PRE_REQUISITO, 0.86));
    }

    @Test
    void listaVaziaEhValida() {
        assertThat(validador.validar(resultado(List.of()))).isEmpty();
    }

    @Test
    void similaridadeAusenteOuNulaEhValidaEViraNula() {
        List<TrechoConhecimento> trechos = validador.validar(resultado(List.of(
                trecho(t -> t.remove("similaridade")), trecho(t -> t.put("similaridade", null)))));

        assertThat(trechos).extracting(TrechoConhecimento::similaridade).containsExactly(null, null);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 1.0})
    void similaridadeNosLimitesEhValida(double valor) {
        assertThat(validador.validar(resultado(List.of(trecho(t -> t.put("similaridade", valor))))))
                .hasSize(1);
    }

    @Test
    void aceitaDezTrechosETextoDe2000Caracteres() {
        List<Map<String, Object>> dez = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dez.add(trecho(t -> t.put("texto", "x".repeat(2_000))));
        }

        assertThat(validador.validar(resultado(dez))).hasSize(10);
    }

    @Test
    void ignoraCamposExtrasSemUsaLos() {
        List<TrechoConhecimento> trechos = validador.validar(resultado(List.of(
                trecho(t -> t.put("status", "PUBLICADO")))));

        assertThat(trechos).hasSize(1);
    }

    // ---- rejeições (cada uma rejeita o resultado inteiro)

    private void assertRejeita(CallToolResult resultado, String trecho) {
        assertThatThrownBy(() -> validador.validar(resultado))
                .isInstanceOf(BaseConhecimentoIndisponivelException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains(trecho));
    }

    @Test
    void rejeitaResultadoNulo() {
        assertRejeita(null, "resultado ausente");
    }

    @Test
    void rejeitaIsErrorEExtraiSoOCodigoConhecido() {
        CallToolResult erro = new CallToolResult(List.of(new TextContent(
                "{\"codigo\":\"BASE_CONHECIMENTO_INDISPONIVEL\",\"mensagem\":\"qualquer coisa\"}\n")), true, null, null);

        assertRejeita(erro, "codigo=BASE_CONHECIMENTO_INDISPONIVEL");
    }

    @Test
    void isErrorComTextoDeOutroFormatoNaoVazaOTexto() {
        CallToolResult erro = new CallToolResult(List.of(new TextContent(
                "Tool (buscar_conhecimento) input validation failed: dados internos 123")), true, null, null);

        assertThatThrownBy(() -> validador.validar(erro)).isInstanceOf(BaseConhecimentoIndisponivelException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("codigo=desconhecido").doesNotContain("123"));
    }

    @Test
    void rejeitaSemStructuredContent() {
        assertRejeita(new CallToolResult(List.of(new TextContent("{}")), false, null, null), "structuredContent ausente");
    }

    @Test
    void rejeitaStructuredContentQueNaoEObjeto() {
        assertRejeita(new CallToolResult(List.of(new TextContent("{}")), false, List.of(1, 2), null), "não é um objeto");
    }

    @Test
    void rejeitaSemTrechosOuTotal() {
        assertRejeita(resultado(Map.of("total", 0)), "'trechos'");
        assertRejeita(resultado(Map.of("trechos", List.of())), "'total'");
        assertRejeita(resultado(Map.of("trechos", "nada", "total", 0)), "'trechos'");
    }

    @Test
    void rejeitaTotalDivergenteOuNegativo() {
        assertRejeita(resultado(Map.of("trechos", List.of(trecho()), "total", 2)), "'total' diferente");
        assertRejeita(resultado(Map.of("trechos", List.of(), "total", -1)), "'total'");
        assertRejeita(resultado(Map.of("trechos", List.of(), "total", 0.5)), "'total'");
    }

    @Test
    void rejeitaMaisDeDezTrechos() {
        List<Map<String, Object>> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onze.add(trecho());
        }

        assertRejeita(resultado(onze), "mais de 10");
    }

    @Test
    void rejeitaIdRepetido() {
        Map<String, Object> a = trecho();
        Map<String, Object> b = trecho(t -> t.put("id", a.get("id")));

        assertRejeita(resultado(List.of(a, b)), "id de trecho repetido");
    }

    @Test
    void umTrechoInvalidoRejeitaOResultadoTodo() {
        assertRejeita(resultado(List.of(trecho(), trecho(t -> t.remove("titulo")))), "trechos[1].titulo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "texto", "documentoId", "titulo", "fonte", "categoria"})
    void rejeitaCampoObrigatorioAusente(String campo) {
        assertRejeita(resultado(List.of(trecho(t -> t.remove(campo)))), "trechos[0]." + campo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "documentoId"})
    void rejeitaUuidInvalido(String campo) {
        assertRejeita(resultado(List.of(trecho(t -> t.put(campo, "isto-nao-e-uuid")))), "não é um UUID");
    }

    @Test
    void rejeitaCategoriaDesconhecida() {
        assertRejeita(resultado(List.of(trecho(t -> t.put("categoria", "OUTRA")))), "categoria desconhecida");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, 2.0, 86.0})
    void rejeitaSimilaridadeForaDeZeroAUm(double valor) {
        assertRejeita(resultado(List.of(trecho(t -> t.put("similaridade", valor)))), "fora de 0 a 1");
    }

    @Test
    void rejeitaSimilaridadeQueNaoEhNumero() {
        assertRejeita(resultado(List.of(trecho(t -> t.put("similaridade", "alta")))), "não é um número");
    }

    @Test
    void rejeitaTextoAcimaDe2000Caracteres() {
        assertRejeita(resultado(List.of(trecho(t -> t.put("texto", "x".repeat(2_001))))), "acima de 2000");
    }

    @Test
    void rejeitaTextoEmBrancoETipoErrado() {
        assertRejeita(resultado(List.of(trecho(t -> t.put("texto", "   ")))), "trechos[0].texto");
        assertRejeita(resultado(List.of(trecho(t -> t.put("titulo", 42)))), "trechos[0].titulo");
    }

    @Test
    void rejeitaTrechoQueNaoEhObjeto() {
        Map<String, Object> estruturado = new LinkedHashMap<>();
        estruturado.put("trechos", List.of("texto solto"));
        estruturado.put("total", 1);

        assertRejeita(resultado(estruturado), "não é um objeto");
    }

    @Test
    void asMensagensNaoTrazemConteudoDoTrecho() {
        Map<String, Object> segredo = trecho(t -> {
            t.put("texto", "conteudo-confidencial-do-trecho");
            t.put("categoria", "INVALIDA");
        });

        assertThatThrownBy(() -> validador.validar(resultado(List.of(segredo))))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("confidencial", "INVALIDA"));
    }
}
