package br.com.coti.atendimentos.infraestrutura.seguranca;

import br.com.coti.atendimentos.api.erro.CodigoErro;
import br.com.coti.atendimentos.api.erro.EscritorProblema;
import br.com.coti.atendimentos.infraestrutura.config.PropriedadesAtendimentos;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite de perguntas por IP (RF06, padrão 10 por minuto): a rota é pública, então este filtro e os limites de
 * tamanho e de tokens são o que segura abuso e custo. Acima do limite responde 429 {@code LIMITE_REQUISICOES} com
 * {@code Retry-After}, antes de qualquer processamento. Toda requisição à rota conta, inclusive as inválidas.
 *
 * <p>O IP é o da conexão. Atrás de um proxy confiável é preciso configurar o tratamento de {@code X-Forwarded-For}
 * do servidor; este filtro não confia em cabeçalho enviado pelo cliente, que poderia ser forjado para fugir do
 * limite.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public final class LimiteRequisicoesFilter extends OncePerRequestFilter {

    private final BaldeTokens balde;
    private final EscritorProblema escritor;

    public LimiteRequisicoesFilter(PropriedadesAtendimentos propriedades, EscritorProblema escritor, Clock relogio) {
        PropriedadesAtendimentos.LimitesRequisicao limites = propriedades.limitesRequisicao();
        this.balde = new BaldeTokens(limites.perguntasPorMinutoPorIp(), Duration.ofMinutes(1), relogio,
                limites.maximoDeIps());
        this.escritor = escritor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && SecurityConfig.ROTA_PERGUNTAR.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        BaldeTokens.Resultado resultado = balde.tentar(request.getRemoteAddr());
        if (!resultado.permitido()) {
            response.setHeader("Retry-After", String.valueOf(resultado.segundosParaTentar()));
            escritor.escrever(request, response, CodigoErro.LIMITE_REQUISICOES);
            return;
        }
        chain.doFilter(request, response);
    }
}
