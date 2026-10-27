package br.com.coti.conhecimento.infraestrutura.seguranca;

import br.com.coti.conhecimento.api.erro.CodigoErro;
import br.com.coti.conhecimento.api.erro.EscritorProblema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limites por credencial administrativa (contrato, seções 3.2 e 3.5): cadastro de conhecimento (padrão 30 por
 * minuto) e busca de diagnóstico (padrão 10 por minuto), cada um com o seu balde. Acima do limite responde 429
 * {@code LIMITE_REQUISICOES} com {@code Retry-After}. Entra na cadeia de segurança logo depois da autenticação,
 * porque a chave é a identidade: requisição sem credencial válida não gasta ficha e recebe 401 mais adiante.
 * Consultas (GET) não têm limite no contrato. Toda requisição às rotas limitadas conta, inclusive as inválidas.
 */
public final class LimiteRequisicoesFilter extends OncePerRequestFilter {

    static final String ROTA_CADASTRO = "/api/conhecimentos";
    static final String ROTA_DIAGNOSTICO = "/api/conhecimentos/diagnostico/busca";

    private final BaldeTokens cadastro;
    private final BaldeTokens diagnostico;
    private final EscritorProblema escritor;

    public LimiteRequisicoesFilter(BaldesDeRequisicao baldes, EscritorProblema escritor) {
        this.cadastro = baldes.cadastro();
        this.diagnostico = baldes.diagnostico();
        this.escritor = escritor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !(ROTA_CADASTRO.equals(request.getRequestURI()) || ROTA_DIAGNOSTICO.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }
        BaldeTokens balde = ROTA_DIAGNOSTICO.equals(request.getRequestURI()) ? diagnostico : cadastro;
        BaldeTokens.Resultado resultado = balde.tentar(autenticacao.getName());
        if (!resultado.permitido()) {
            response.setHeader("Retry-After", String.valueOf(resultado.segundosParaTentar()));
            escritor.escrever(request, response, CodigoErro.LIMITE_REQUISICOES);
            return;
        }
        chain.doFilter(request, response);
    }
}
