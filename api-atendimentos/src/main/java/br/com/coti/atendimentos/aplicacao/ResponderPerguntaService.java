package br.com.coti.atendimentos.aplicacao;

import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.aplicacao.porta.GeradorRespostaPort;
import br.com.coti.atendimentos.aplicacao.porta.MascaradorPort;
import br.com.coti.atendimentos.dominio.ContextoRag;
import br.com.coti.atendimentos.dominio.PerguntaAluno;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.ResultadoAtendimento;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import br.com.coti.atendimentos.dominio.excecao.SaidaModeloInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fluxo B do atendimento (RF06 a RF10). Workflow fixo em código, sem agente: mascara a pergunta, busca na base
 * pela ferramenta MCP, monta o contexto, responde "sem evidência" quando não sobra trecho (o modelo nunca é
 * chamado nesse caso), gera a resposta, valida a saída e deriva as fontes do resultado MCP. Saída reprovada volta
 * ao modelo com o motivo, até {@code novasTentativasSaida} vezes; persistindo, o aluno recebe "sem evidência".
 * Falhas de dependência sobem como exceções de domínio e viram 503 na borda; nenhuma gera resposta parcial ou
 * inventada.
 *
 * <p>A pergunta e os textos nunca são registrados: o log tem só tamanho, quantidades e duração.
 */
public final class ResponderPerguntaService {

    private static final Logger log = LoggerFactory.getLogger(ResponderPerguntaService.class);

    /** Mensagem de recusa e canal humano, ambos de configuração (RN10.2). */
    public record MensagensSemEvidencia(String mensagem, String canalHumano) {

        String texto() {
            return mensagem.strip() + " " + canalHumano.strip();
        }
    }

    private final MascaradorPort mascarador;
    private final BaseConhecimentoPort base;
    private final MontadorContextoRag montador;
    private final GeradorRespostaPort gerador;
    private final ValidadorRespostaModelo validador;
    private final DerivadorFontes derivador;
    private final MensagensSemEvidencia mensagens;
    private final int limiteDeTrechos;
    private final Duration prazoTotal;
    private final int novasTentativasSaida;
    private final Clock relogio;

    public ResponderPerguntaService(MascaradorPort mascarador, BaseConhecimentoPort base, MontadorContextoRag montador,
                                    GeradorRespostaPort gerador, ValidadorRespostaModelo validador,
                                    DerivadorFontes derivador, MensagensSemEvidencia mensagens, int limiteDeTrechos,
                                    Duration prazoTotal, int novasTentativasSaida, Clock relogio) {
        this.mascarador = mascarador;
        this.base = base;
        this.montador = montador;
        this.gerador = gerador;
        this.validador = validador;
        this.derivador = derivador;
        this.mensagens = mensagens;
        this.limiteDeTrechos = limiteDeTrechos;
        this.prazoTotal = prazoTotal;
        this.novasTentativasSaida = novasTentativasSaida;
        this.relogio = relogio;
    }

    public ResultadoAtendimento responder(PerguntaAluno pergunta) {
        Prazo prazo = Prazo.iniciar(relogio, prazoTotal);
        String mascarada = limitar(mascarador.mascarar(pergunta.texto()));

        List<TrechoConhecimento> trechos = base.buscar(mascarada, limiteDeTrechos, prazo);
        prazo.exigirTempo();
        ContextoRag contexto = montador.montar(trechos);
        log.info("Contexto montado. tamanhoPergunta={} trechosRecuperados={} trechosNoContexto={}", mascarada.length(),
                trechos.size(), contexto.referencias().size());
        if (contexto.vazio()) {
            return semEvidencia();
        }
        // A pergunta também é dado não confiável: não pode fechar o bloco <pergunta> nem abrir <trechos> no prompt.
        return gerarEValidar(MontadorContextoRag.sanitizarTexto(mascarada), contexto, prazo);
    }

    private ResultadoAtendimento gerarEValidar(String mascarada, ContextoRag contexto, Prazo prazo) {
        String motivo = null;
        for (int tentativa = 0; tentativa <= novasTentativasSaida; tentativa++) {
            prazo.exigirTempo();
            Optional<String> reprovacao;
            RespostaGerada gerada = null;
            try {
                gerada = gerador.gerar(mascarada, contexto, motivo, prazo);
                prazo.exigirTempo();
                reprovacao = validador.validar(gerada, contexto);
            } catch (SaidaModeloInvalidaException falha) {
                reprovacao = Optional.of("A saída não veio no formato JSON esperado (resposta, conhecimentoSuficiente, "
                        + "referencias).");
            }
            if (reprovacao.isEmpty()) {
                return aprovada(gerada, contexto);
            }
            motivo = reprovacao.get();
            log.warn("Saída do modelo reprovada. tentativa={} de {}", tentativa + 1, novasTentativasSaida + 1);
        }
        return semEvidencia();
    }

    private ResultadoAtendimento aprovada(RespostaGerada gerada, ContextoRag contexto) {
        if (!gerada.conhecimentoSuficiente()) {
            return semEvidencia();
        }
        return new ResultadoAtendimento(gerada.resposta().strip(), true,
                derivador.derivar(gerada.referencias(), contexto), agora());
    }

    private ResultadoAtendimento semEvidencia() {
        return ResultadoAtendimento.semEvidencia(mensagens.texto(), agora());
    }

    /**
     * O mascarador pode trocar um e-mail curto por um marcador mais longo; o servidor recusa pergunta acima de
     * {@value PerguntaAluno#TAMANHO_MAXIMO} caracteres.
     */
    private static String limitar(String texto) {
        return texto.length() <= PerguntaAluno.TAMANHO_MAXIMO ? texto : texto.substring(0, PerguntaAluno.TAMANHO_MAXIMO);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.SECONDS);
    }
}
