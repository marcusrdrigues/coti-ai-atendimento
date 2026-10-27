package br.com.coti.conhecimento.infraestrutura.seguranca;

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
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a chamada de serviço a serviço por {@code Authorization: Bearer <chave>}. Chave ausente, de outro
 * esquema ou incorreta apenas deixa a requisição sem autenticação: quem responde 401 é a regra de autorização
 * da cadeia, antes de qualquer processamento MCP. A chave administrativa ({@code X-Api-Key}) não é lida aqui.
 */
public final class BearerAuthFilter extends OncePerRequestFilter {

    private static final String ESQUEMA = "Bearer ";

    /** Guarda o contexto na requisição: a resposta MCP é assíncrona (SSE) e o redespacho precisa da mesma identidade. */
    private final SecurityContextRepository repositorio = new RequestAttributeSecurityContextRepository();

    private final String chaveEsperada;
    private final String principal;
    private final List<GrantedAuthority> autoridades;

    public BearerAuthFilter(String chaveEsperada, String principal, String papel) {
        this.chaveEsperada = chaveEsperada;
        this.principal = principal;
        this.autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + papel));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho != null && cabecalho.regionMatches(true, 0, ESQUEMA, 0, ESQUEMA.length())) {
            String recebida = cabecalho.substring(ESQUEMA.length()).strip();
            if (ComparadorChave.iguais(recebida, chaveEsperada)) {
                SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, autoridades));
                SecurityContextHolder.setContext(contexto);
                repositorio.saveContext(contexto, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
