package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.Pagina;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dublê em memória da porta de persistência, com as mesmas regras de unicidade e de listagem do banco. */
class RepositorioDocumentoEmMemoria implements RepositorioDocumentoPort {

    final Map<UUID, Documento> documentos = new LinkedHashMap<>();
    int inserts;
    int atualizacoes;
    boolean falharAoAtualizar;

    @Override
    public void inserir(Documento documento) {
        if (buscarPorHash(documento.hashConteudo()).isPresent()) {
            throw new ConhecimentoDuplicadoException();
        }
        inserts++;
        documentos.put(documento.id(), documento);
    }

    @Override
    public Optional<Documento> buscarPorHash(String hashConteudo) {
        return documentos.values().stream().filter(d -> d.hashConteudo().equals(hashConteudo)).findFirst();
    }

    @Override
    public Optional<Documento> buscarPorId(UUID id) {
        return Optional.ofNullable(documentos.get(id));
    }

    @Override
    public void atualizarEstado(Documento documento) {
        if (falharAoAtualizar) {
            throw new IllegalStateException("falha simulada ao atualizar");
        }
        atualizacoes++;
        documentos.put(documento.id(), documento);
    }

    @Override
    public List<Documento> buscarProcessandoAnterioresA(java.time.OffsetDateTime limite) {
        return documentos.values().stream()
                .filter(d -> d.status() == StatusDocumento.PROCESSANDO && d.cadastradoEm().isBefore(limite)).toList();
    }

    @Override
    public Pagina<DocumentoResumo> listar(FiltroDocumentos filtro, int pagina, int tamanho) {
        List<DocumentoResumo> todos = new ArrayList<>(documentos.values().stream()
                .filter(d -> d.status() != StatusDocumento.PROCESSANDO)
                .filter(d -> filtro.status() == null || d.status() == filtro.status())
                .filter(d -> filtro.categoria() == null || d.categoria() == filtro.categoria())
                .sorted(Comparator.comparing(Documento::cadastradoEm).reversed())
                .map(Documento::resumo)
                .toList());
        int de = Math.min(pagina * tamanho, todos.size());
        int ate = Math.min(de + tamanho, todos.size());
        return new Pagina<>(todos.subList(de, ate), pagina, tamanho, todos.size());
    }
}
