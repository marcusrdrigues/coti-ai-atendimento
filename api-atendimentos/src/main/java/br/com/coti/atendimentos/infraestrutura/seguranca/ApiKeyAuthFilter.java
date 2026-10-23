package br.com.coti.atendimentos.infraestrutura.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica por chave de API no cabeçalho {@code X-Api-Key}. Chave ausente ou incorreta apenas deixa a
 * requisição sem autenticação: quem responde 401 ou 403 é a regra de autorização da cadeia.
 */
public final class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Api-Key";

    private final String chaveEsperada;
    private final String principal;
    private final List<GrantedAuthority> autoridades;

    public ApiKeyAuthFilter(String chaveEsperada, String principal, String papel) {
        this.chaveEsperada = chaveEsperada;
        this.principal = principal;
        this.autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebida = request.getHeader(CABECALHO);
        if (ComparadorChave.iguais(recebida, chaveEsperada)) {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, autoridades));
            SecurityContextHolder.setContext(contexto);
        }
        chain.doFilter(request, response);
    }
}
