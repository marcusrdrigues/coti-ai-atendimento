package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.AnalisadorConteudoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.DivisorTexto;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.NormalizadorTexto;
import br.com.coti.conhecimento.dominio.ResultadoAnalise;
import br.com.coti.conhecimento.dominio.Trecho;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.dominio.excecao.DadoPessoalDetectadoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.IndexacaoFalhouException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cadastro de conhecimento (RF01 a RF03). Ordem: normalizar → hash e duplicidade → análise do conteúdo →
 * decisão: dado pessoal recusa sem gravar; sinal de injeção grava {@code EM_REVISAO} sem indexar; caso contrário
 * divide em trechos (documento grande demais é recusado antes de gravar qualquer coisa), grava
 * {@code PROCESSANDO}, grava os trechos em lotes e publica numa transação curta. Se qualquer passo da indexação
 * falhar, os trechos já gravados são apagados e o documento fica {@code ERRO_PROCESSAMENTO}: nunca há
 * publicação parcial.
 */
@Service
public final class CadastrarConhecimentoService {

    static final int CONTEUDO_MINIMO = 50;
    static final int CONTEUDO_MAXIMO = 100_000;

    private static final Logger log = LoggerFactory.getLogger(CadastrarConhecimentoService.class);

    private final RepositorioDocumentoPort repositorio;
    private final RepositorioVetorialPort vetorial;
    private final AnalisadorConteudoPort analisador;
    private final DivisorTexto divisor;
    private final IndexarDocumentoService indexacao;
    private final PublicarDocumentoService publicacao;
    private final ModeloEmbedding modeloEmbedding;
    private final Clock relogio;

    public CadastrarConhecimentoService(RepositorioDocumentoPort repositorio, RepositorioVetorialPort vetorial,
            AnalisadorConteudoPort analisador, DivisorTexto divisor, IndexarDocumentoService indexacao,
            PublicarDocumentoService publicacao, ModeloEmbedding modeloEmbedding, Clock relogio) {
        this.repositorio = repositorio;
        this.vetorial = vetorial;
        this.analisador = analisador;
        this.divisor = divisor;
        this.indexacao = indexacao;
        this.publicacao = publicacao;
        this.modeloEmbedding = modeloEmbedding;
        this.relogio = relogio;
    }

    public Documento cadastrar(CadastrarConhecimentoComando comando) {
        String texto = NormalizadorTexto.normalizar(comando.conteudo());
        if (texto.length() < CONTEUDO_MINIMO || texto.length() > CONTEUDO_MAXIMO) {
            throw new DadosInvalidosException("conteudo",
                    "deve ter entre " + CONTEUDO_MINIMO + " e " + CONTEUDO_MAXIMO + " caracteres após a normalização");
        }
        String hash = HashConteudo.sha256(texto);
        if (repositorio.buscarPorHash(hash).isPresent()) {
            throw new ConhecimentoDuplicadoException();
        }

        ResultadoAnalise analise = analisador.analisar(texto);
        if (analise.contemDadoPessoal()) {
            throw new DadoPessoalDetectadoException();
        }

        UUID id = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        if (!analise.pendencias().isEmpty()) {
            Documento emRevisao = Documento.emRevisao(id, comando.titulo(), texto, comando.fonte(),
                    comando.categoria(), hash, modeloEmbedding.nome(), comando.vigenteAte(),
                    comando.cadastradoPor(), agora, analise.pendencias());
            repositorio.inserir(emRevisao);
            log.info("Conhecimento retido para revisão. conhecimentoId={} pendencias={}", id, analise.pendencias());
            return emRevisao;
        }

        // Pode lançar DocumentoGrandeDemaisException: acontece antes de gravar qualquer coisa.
        List<Trecho> trechos = divisor.dividir(id, texto);

        Documento processando = Documento.emProcessamento(id, comando.titulo(), texto, comando.fonte(),
                comando.categoria(), hash, modeloEmbedding.nome(), comando.vigenteAte(),
                comando.cadastradoPor(), agora);
        repositorio.inserir(processando);
        return indexarEPublicar(processando, trechos);
    }

    private Documento indexarEPublicar(Documento processando, List<Trecho> trechos) {
        try {
            indexacao.indexar(processando, trechos);
            Documento publicado = publicacao.publicar(processando, trechos.size());
            log.info("Conhecimento publicado. conhecimentoId={} trechos={}", publicado.id(), trechos.size());
            return publicado;
        } catch (RuntimeException falha) {
            log.warn("Falha ao indexar o conhecimento. conhecimentoId={}", processando.id(), falha);
            compensar(processando, falha);
            throw new IndexacaoFalhouException(processando.id(), falha);
        }
    }

    /** Apaga os trechos já gravados e registra o erro. Falhas aqui não escondem a causa original. */
    private void compensar(Documento processando, RuntimeException falhaOriginal) {
        try {
            vetorial.removerPorDocumento(processando.id());
        } catch (RuntimeException falhaAoRemover) {
            falhaOriginal.addSuppressed(falhaAoRemover);
            log.error("Não foi possível apagar os trechos. conhecimentoId={}", processando.id(), falhaAoRemover);
        }
        try {
            repositorio.atualizarEstado(processando.falharProcessamento());
        } catch (RuntimeException falhaAoMarcar) {
            falhaOriginal.addSuppressed(falhaAoMarcar);
            log.error("Não foi possível marcar ERRO_PROCESSAMENTO. conhecimentoId={}", processando.id(),
                    falhaAoMarcar);
        }
    }
}
