package br.com.coti.conhecimento.aplicacao.porta;

import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.Pagina;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos documentos (tabela {@code documento}); os trechos vetoriais ficam em outra porta. */
public interface RepositorioDocumentoPort {

    /**
     * Grava um documento novo.
     *
     * @throws br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException se já existe um
     *         documento com o mesmo hash de conteúdo
     */
    void inserir(Documento documento);

    Optional<Documento> buscarPorHash(String hashConteudo);

    Optional<Documento> buscarPorId(UUID id);

    /** Atualiza estado, quantidade de trechos e pendências de um documento existente. */
    void atualizarEstado(Documento documento);

    /** Documentos ainda {@code PROCESSANDO} cadastrados antes do instante dado (processo interrompido). */
    List<Documento> buscarProcessandoAnterioresA(OffsetDateTime limite);

    /**
     * Lista os documentos visíveis ao gestor (nunca {@code PROCESSANDO}), do mais recente para o mais antigo.
     * Não traz o conteúdo.
     */
    Pagina<DocumentoResumo> listar(FiltroDocumentos filtro, int pagina, int tamanho);
}
