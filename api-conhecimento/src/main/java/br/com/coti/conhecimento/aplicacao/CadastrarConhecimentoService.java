package br.com.coti.conhecimento.aplicacao;

import br.com.coti.conhecimento.aplicacao.porta.AnalisadorConteudoPort;
import br.com.coti.conhecimento.aplicacao.porta.IndexacaoDocumentoPort;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import br.com.coti.conhecimento.dominio.Documento;
import br.com.coti.conhecimento.dominio.HashConteudo;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import br.com.coti.conhecimento.dominio.NormalizadorTexto;
import br.com.coti.conhecimento.dominio.ResultadoAnalise;
import br.com.coti.conhecimento.dominio.excecao.ConhecimentoDuplicadoException;
import br.com.coti.conhecimento.dominio.excecao.DadoPessoalDetectadoException;
import br.com.coti.conhecimento.dominio.excecao.DadosInvalidosException;
import br.com.coti.conhecimento.dominio.excecao.IndexacaoFalhouException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cadastro de conhecimento (RF01). Ordem: normalizar → hash e duplicidade → análise do conteúdo → decisão:
 * dado pessoal recusa sem gravar; sinal de injeção grava {@code EM_REVISAO} sem indexar; caso contrário grava
 * {@code PROCESSANDO}, indexa e publica (ou marca {@code ERRO_PROCESSAMENTO} se a indexação falhar).
 */
@Service
public final class CadastrarConhecimentoService {

    static final int CONTEUDO_MINIMO = 50;
    static final int CONTEUDO_MAXIMO = 100_000;

    private static final Logger log = LoggerFactory.getLogger(CadastrarConhecimentoService.class);

    private final RepositorioDocumentoPort repositorio;
    private final AnalisadorConteudoPort analisador;
    private final IndexacaoDocumentoPort indexacao;
    private final ModeloEmbedding modeloEmbedding;
    private final Clock relogio;

    public CadastrarConhecimentoService(RepositorioDocumentoPort repositorio, AnalisadorConteudoPort analisador,
            IndexacaoDocumentoPort indexacao, ModeloEmbedding modeloEmbedding, Clock relogio) {
        this.repositorio = repositorio;
        this.analisador = analisador;
        this.indexacao = indexacao;
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

        Documento processando = Documento.emProcessamento(id, comando.titulo(), texto, comando.fonte(),
                comando.categoria(), hash, modeloEmbedding.nome(), comando.vigenteAte(),
                comando.cadastradoPor(), agora);
        repositorio.inserir(processando);
        return indexar(processando);
    }

    private Documento indexar(Documento processando) {
        try {
            int trechos = indexacao.indexar(processando);
            Documento publicado = processando.publicar(trechos);
            repositorio.atualizarEstado(publicado);
            log.info("Conhecimento publicado. conhecimentoId={} trechos={}", publicado.id(), trechos);
            return publicado;
        } catch (RuntimeException falha) {
            log.warn("Falha ao indexar o conhecimento. conhecimentoId={}", processando.id(), falha);
            marcarErro(processando, falha);
            throw new IndexacaoFalhouException(processando.id(), falha);
        }
    }

    private void marcarErro(Documento processando, RuntimeException falhaOriginal) {
        try {
            repositorio.atualizarEstado(processando.falharProcessamento());
        } catch (RuntimeException falhaAoMarcar) {
            falhaOriginal.addSuppressed(falhaAoMarcar);
            log.error("Não foi possível marcar ERRO_PROCESSAMENTO. conhecimentoId={}", processando.id(),
                    falhaAoMarcar);
        }
    }
}
