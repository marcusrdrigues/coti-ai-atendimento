package br.com.coti.atendimentos.aplicacao;

import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.aplicacao.porta.GeradorRespostaPort;
import br.com.coti.atendimentos.aplicacao.porta.MascaradorPort;
import br.com.coti.atendimentos.dominio.Fonte;
import br.com.coti.atendimentos.dominio.PerguntaAluno;
import br.com.coti.atendimentos.dominio.Prazo;
import br.com.coti.atendimentos.dominio.RespostaGerada;
import br.com.coti.atendimentos.dominio.ResultadoAtendimento;
import br.com.coti.atendimentos.dominio.TrechoConhecimento;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fluxo B do atendimento (RF06 a RF10). Workflow fixo em código, sem agente: mascara a pergunta, busca na base
 * pela ferramenta MCP, responde "sem evidência" quando não há trechos (o gerador nunca é chamado nesse caso) e só
 * então gera a resposta. Falhas de dependência sobem como exceções de domínio e viram 503 na borda; nenhuma gera
 * resposta parcial ou inventada.
 *
 * <p>A pergunta nunca é registrada: o log tem só o tamanho, a quantidade de trechos e a duração.
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
    private final GeradorRespostaPort gerador;
    private final MensagensSemEvidencia mensagens;
    private final int limiteDeTrechos;
    private final Duration prazoTotal;
    private final Clock relogio;

    public ResponderPerguntaService(MascaradorPort mascarador, BaseConhecimentoPort base, GeradorRespostaPort gerador,
                                    MensagensSemEvidencia mensagens, int limiteDeTrechos, Duration prazoTotal,
                                    Clock relogio) {
        this.mascarador = mascarador;
        this.base = base;
        this.gerador = gerador;
        this.mensagens = mensagens;
        this.limiteDeTrechos = limiteDeTrechos;
        this.prazoTotal = prazoTotal;
        this.relogio = relogio;
    }

    public ResultadoAtendimento responder(PerguntaAluno pergunta) {
        Prazo prazo = Prazo.iniciar(relogio, prazoTotal);
        String mascarada = limitar(mascarador.mascarar(pergunta.texto()));

        List<TrechoConhecimento> trechos = base.buscar(mascarada, limiteDeTrechos, prazo);
        prazo.exigirTempo();
        log.info("Busca concluída. tamanhoPergunta={} trechos={}", mascarada.length(), trechos.size());

        if (trechos.isEmpty()) {
            return semEvidencia();
        }
        RespostaGerada gerada = gerador.gerar(mascarada, trechos, prazo);
        prazo.exigirTempo();
        if (!gerada.conhecimentoSuficiente()) {
            return semEvidencia();
        }
        return new ResultadoAtendimento(gerada.resposta(), true,
                gerada.citados().stream().map(Fonte::de).distinct().toList(), agora());
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
