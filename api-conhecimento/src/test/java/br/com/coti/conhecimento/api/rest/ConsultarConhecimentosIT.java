package br.com.coti.conhecimento.api.rest;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.dominio.Categoria;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.Pendencia;
import br.com.coti.conhecimento.suporte.ChavesTeste;
import br.com.coti.conhecimento.suporte.TesteWeb;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** GET /api/conhecimentos e GET /api/conhecimentos/{id} (RF11), com o banco real. */
@TesteWeb
class ConsultarConhecimentosIT {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-10-23T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RepositorioDocumentoPort repositorio;

    @BeforeEach
    void limpar() {
        jdbc.sql("DELETE FROM documento").update();
    }

    private Documento publicado(String chave, Categoria categoria, int minutos, LocalDate vigencia) {
        Documento processando = Documento.emProcessamento(UUID.randomUUID(), "Título " + chave,
                "Conteúdo secreto-" + chave, "Fonte " + chave, categoria, HashConteudo.sha256(chave),
                "text-embedding-3-small", vigencia, "gestor-conteudo", BASE.plusMinutes(minutos));
        repositorio.inserir(processando);
        Documento publicado = processando.publicar(3);
        repositorio.atualizarEstado(publicado);
        return publicado;
    }

    private Documento emRevisao(String chave, int minutos) {
        Documento documento = Documento.emRevisao(UUID.randomUUID(), "Título " + chave, "Conteúdo secreto-" + chave,
                "Fonte " + chave, Categoria.CURSO, HashConteudo.sha256(chave), "text-embedding-3-small", null,
                "gestor-conteudo", BASE.plusMinutes(minutos), List.of(Pendencia.POSSIVEL_PROMPT_INJECTION));
        repositorio.inserir(documento);
        return documento;
    }

    private ResultActions consultar(String caminho) throws Exception {
        return consultar(get(caminho));
    }

    private ResultActions consultar(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.header("X-Api-Key", ChavesTeste.ADMIN));
    }

    // ---- listagem

    @Test
    void deveListarComPaginacaoPadraoDoMaisRecenteParaOMaisAntigo() throws Exception {
        publicado("a", Categoria.CURSO, 1, null);
        publicado("b", Categoria.CURSO, 3, null);
        publicado("c", Categoria.CURSO, 2, null);

        consultar("/api/conhecimentos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalItens").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.itens", hasSize(3)))
                .andExpect(jsonPath("$.itens[0].titulo").value("Título b"))
                .andExpect(jsonPath("$.itens[1].titulo").value("Título c"))
                .andExpect(jsonPath("$.itens[2].titulo").value("Título a"));
    }

    @Test
    void listagemNuncaTrazOConteudoEMostraOsCamposDoResumo() throws Exception {
        publicado("a", Categoria.PRE_REQUISITO, 1, LocalDate.of(2027, 6, 30));

        consultar("/api/conhecimentos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].conteudo").doesNotExist())
                .andExpect(content().string(not(containsString("secreto-a"))))
                .andExpect(jsonPath("$.itens[0].id").isNotEmpty())
                .andExpect(jsonPath("$.itens[0].fonte").value("Fonte a"))
                .andExpect(jsonPath("$.itens[0].categoria").value("PRE_REQUISITO"))
                .andExpect(jsonPath("$.itens[0].status").value("PUBLICADO"))
                .andExpect(jsonPath("$.itens[0].quantidadeChunks").value(3))
                .andExpect(jsonPath("$.itens[0].vigenteAte").value("2027-06-30"))
                .andExpect(jsonPath("$.itens[0].cadastradoEm").isNotEmpty());
    }

    @Test
    void listagemOmiteVigenciaQuandoNaoHa() throws Exception {
        publicado("a", Categoria.CURSO, 1, null);

        consultar("/api/conhecimentos").andExpect(jsonPath("$.itens[0].vigenteAte").doesNotExist());
    }

    @Test
    void deveFiltrarPorStatus() throws Exception {
        publicado("a", Categoria.CURSO, 1, null);
        publicado("b", Categoria.CURSO, 2, null);
        emRevisao("c", 3);

        consultar("/api/conhecimentos?status=EM_REVISAO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].titulo").value("Título c"))
                .andExpect(jsonPath("$.itens[0].status").value("EM_REVISAO"));
    }

    @Test
    void deveFiltrarPorCategoriaECombinarFiltros() throws Exception {
        publicado("a", Categoria.CURSO, 1, null);
        publicado("b", Categoria.MODALIDADE, 2, null);
        emRevisao("c", 3);

        consultar("/api/conhecimentos?categoria=MODALIDADE")
                .andExpect(jsonPath("$.itens", hasSize(1))).andExpect(jsonPath("$.itens[0].titulo").value("Título b"));
        consultar("/api/conhecimentos?categoria=CURSO&status=PUBLICADO")
                .andExpect(jsonPath("$.itens", hasSize(1))).andExpect(jsonPath("$.itens[0].titulo").value("Título a"));
        consultar("/api/conhecimentos?categoria=INSTITUCIONAL").andExpect(jsonPath("$.itens", hasSize(0)));
    }

    @Test
    void devePaginar() throws Exception {
        for (int i = 0; i < 5; i++) {
            publicado("p" + i, Categoria.CURSO, i, null);
        }

        consultar("/api/conhecimentos?pagina=2&tamanho=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.totalItens").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.itens", hasSize(1)));
    }

    @Test
    void listagemVaziaEhSucesso() throws Exception {
        consultar("/api/conhecimentos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(0)))
                .andExpect(jsonPath("$.totalItens").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void listagemEscondeDocumentosEmProcessamento() throws Exception {
        publicado("a", Categoria.CURSO, 1, null);
        jdbc.sql("""
                INSERT INTO documento (id, titulo, conteudo, fonte, categoria, status, hash_conteudo,
                    quantidade_chunks, modelo_embedding, cadastrado_por, cadastrado_em)
                VALUES (:id, 'em andamento', 'c', 'f', 'CURSO', 'PROCESSANDO', :hash, 0, 'm', 'g', now())""")
                .param("id", UUID.randomUUID()).param("hash", HashConteudo.sha256("andamento")).update();

        consultar("/api/conhecimentos")
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(content().string(not(containsString("em andamento"))));
    }

    @Test
    void deveRecusarTamanhoForaDoIntervalo() throws Exception {
        for (String tamanho : List.of("101", "0", "-1", "1000")) {
            consultar("/api/conhecimentos?tamanho=" + tamanho)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.erros[0].campo").value("tamanho"));
        }
        consultar("/api/conhecimentos?tamanho=100").andExpect(status().isOk());
        consultar("/api/conhecimentos?tamanho=1").andExpect(status().isOk());
    }

    @Test
    void deveRecusarPaginaNegativaEParametrosMalFormados() throws Exception {
        consultar("/api/conhecimentos?pagina=-1")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("pagina"));
        consultar("/api/conhecimentos?pagina=abc")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("pagina"))
                .andExpect(jsonPath("$.erros[0].mensagem").value(not(containsString("abc"))));
        consultar("/api/conhecimentos?status=INVENTADO")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("status"));
        consultar("/api/conhecimentos?categoria=INVENTADA")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("categoria"));
    }

    @Test
    void naoDeveAceitarFiltrarPorProcessando() throws Exception {
        consultar("/api/conhecimentos?status=PROCESSANDO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("status"))
                .andExpect(jsonPath("$.erros[0].mensagem").value("valor não permitido"));
    }

    @Test
    void parametrosComSqlSaoTratadosComoValorInvalido() throws Exception {
        consultar(get("/api/conhecimentos").param("categoria", "CURSO' OR '1'='1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros[0].campo").value("categoria"));
    }

    // ---- detalhe

    @Test
    void deveDetalharComConteudoAutorEModelo() throws Exception {
        Documento documento = publicado("a", Categoria.CURSO, 1, LocalDate.of(2027, 6, 30));

        consultar("/api/conhecimentos/" + documento.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documento.id().toString()))
                .andExpect(jsonPath("$.titulo").value("Título a"))
                .andExpect(jsonPath("$.conteudo").value("Conteúdo secreto-a"))
                .andExpect(jsonPath("$.fonte").value("Fonte a"))
                .andExpect(jsonPath("$.categoria").value("CURSO"))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.quantidadeChunks").value(3))
                .andExpect(jsonPath("$.modeloEmbedding").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.vigenteAte").value("2027-06-30"))
                .andExpect(jsonPath("$.cadastradoEm").isNotEmpty())
                .andExpect(jsonPath("$.cadastradoPor").value("gestor-conteudo"))
                .andExpect(jsonPath("$.pendencias").doesNotExist());
    }

    @Test
    void detalheDeDocumentoEmRevisaoMostraPendencias() throws Exception {
        Documento documento = emRevisao("r", 1);

        consultar("/api/conhecimentos/" + documento.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_REVISAO"))
                .andExpect(jsonPath("$.quantidadeChunks").value(0))
                .andExpect(jsonPath("$.pendencias[0]").value("POSSIVEL_PROMPT_INJECTION"))
                .andExpect(jsonPath("$.conteudo").value("Conteúdo secreto-r"));
    }

    @Test
    void deveRetornar404ParaIdInexistente() throws Exception {
        consultar("/api/conhecimentos/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONHECIMENTO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.title").value("Conhecimento não encontrado"));
    }

    @Test
    void deveRetornar404ParaDocumentoEmProcessamento() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO documento (id, titulo, conteudo, fonte, categoria, status, hash_conteudo,
                    quantidade_chunks, modelo_embedding, cadastrado_por, cadastrado_em)
                VALUES (:id, 't', 'c', 'f', 'CURSO', 'PROCESSANDO', :hash, 0, 'm', 'g', now())""")
                .param("id", id).param("hash", HashConteudo.sha256("proc")).update();

        consultar("/api/conhecimentos/" + id).andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400ParaIdQueNaoEUuid() throws Exception {
        consultar("/api/conhecimentos/nao-e-um-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.erros[0].campo").value("id"));
    }

    // ---- credenciais

    @Test
    void deveExigirAChaveAdministrativaNasDuasConsultas() throws Exception {
        Documento documento = publicado("a", Categoria.CURSO, 1, null);

        for (String caminho : List.of("/api/conhecimentos", "/api/conhecimentos/" + documento.id())) {
            mvc.perform(get(caminho)).andExpect(status().isUnauthorized());
            mvc.perform(get(caminho).header("X-Api-Key", "errada")).andExpect(status().isUnauthorized());
            mvc.perform(get(caminho).header("X-Api-Key", ChavesTeste.MONITORAMENTO)).andExpect(status().isUnauthorized());
        }
    }
}
