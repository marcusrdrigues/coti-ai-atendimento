package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DocumentoResumo;
import br.com.coti.conhecimento.dominio.FiltroDocumentos;
import br.com.coti.conhecimento.dominio.Pagina;
import br.com.coti.conhecimento.dominio.StatusDocumento;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de conhecimentos pelo gestor (RF11). {@code PROCESSANDO} é interno e transitório: não aparece na
 * listagem nem no detalhe. Não é {@code final} porque a transação de leitura exige proxy de classe.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarConhecimentosService {

    public static final int TAMANHO_MAXIMO = 100;

    private final RepositorioDocumentoPort repositorio;

    public ConsultarConhecimentosService(RepositorioDocumentoPort repositorio) {
        this.repositorio = repositorio;
    }

    public Pagina<DocumentoResumo> listar(FiltroDocumentos filtro, int pagina, int tamanho) {
        if (filtro.status() == StatusDocumento.PROCESSANDO) {
            throw new DadosInvalidosException("status", "valor não permitido");
        }
        if (pagina < 0) {
            throw new DadosInvalidosException("pagina", "deve ser maior ou igual a 0");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new DadosInvalidosException("tamanho", "deve estar entre 1 e " + TAMANHO_MAXIMO);
        }
        return repositorio.listar(filtro, pagina, tamanho);
    }

    public Documento detalhar(UUID id) {
        return repositorio.buscarPorId(id)
                .filter(documento -> documento.status().visivelAoGestor())
                .orElseThrow(() -> new ConhecimentoNaoEncontradoException(id));
    }
}
