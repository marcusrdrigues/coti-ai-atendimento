package br.com.coti.conhecimento.infraestrutura.seguranca;

import br.com.coti.conhecimento.infraestrutura.config.PropriedadesConhecimento;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Segurança da api-conhecimento: sem sessão, sem CSRF (API de chave), CORS desligado e negação por padrão.
 *
 * <ul>
 *   <li>{@code /actuator/**} e documentação: chave de monitoramento (health e info públicos);</li>
 *   <li>{@code /api/**}: chave administrativa, papel GESTOR;</li>
 *   <li>{@code /mcp}: chave de serviço (Bearer), papel SERVICO_ATENDIMENTO;</li>
 *   <li>qualquer outra rota: exige autenticação, que nenhuma credencial satisfaz (401).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String PAPEL_GESTOR = "GESTOR";
    static final String PAPEL_MONITORAMENTO = "MONITORAMENTO";
    static final String PAPEL_SERVICO_ATENDIMENTO = "SERVICO_ATENDIMENTO";

    @Bean
    @Order(1)
    SecurityFilterChain cadeiaMonitoramento(HttpSecurity http, PropriedadesConhecimento propriedades,
                                            RespostaSegurancaProblema resposta) throws Exception {
        boolean docsPublica = propriedades.documentacaoPublica();
        http.securityMatcher("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
        http.authorizeHttpRequests(a -> {
            a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            a.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
            if (docsPublica) {
                a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
            }
            a.anyRequest().hasRole(PAPEL_MONITORAMENTO);
        });
        return comBase(http, resposta)
                .addFilterBefore(new ApiKeyAuthFilter(propriedades.seguranca().monitoramentoApiKey(),
                        "monitoramento", PAPEL_MONITORAMENTO), AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain cadeiaApi(HttpSecurity http, PropriedadesConhecimento propriedades,
                                  RespostaSegurancaProblema resposta) throws Exception {
        http.securityMatcher("/api/**");
        http.authorizeHttpRequests(a -> {
            a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            a.anyRequest().hasRole(PAPEL_GESTOR);
        });
        return comBase(http, resposta)
                .addFilterBefore(new ApiKeyAuthFilter(propriedades.seguranca().adminApiKey(),
                        "gestor-conteudo", PAPEL_GESTOR), AnonymousAuthenticationFilter.class)
                .build();
    }

    /**
     * Ferramenta MCP: somente a chave de serviço da {@code api-atendimentos}, em {@code Authorization: Bearer}.
     * Sem credencial, com chave errada ou com a chave administrativa a resposta é 401, antes do protocolo MCP.
     */
    @Bean
    @Order(3)
    SecurityFilterChain cadeiaMcp(HttpSecurity http, PropriedadesConhecimento propriedades,
                                  RespostaSegurancaProblema resposta) throws Exception {
        http.securityMatcher("/mcp", "/mcp/**");
        http.authorizeHttpRequests(a -> {
            a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            a.anyRequest().hasRole(PAPEL_SERVICO_ATENDIMENTO);
        });
        return comBase(http, resposta)
                .addFilterBefore(new BearerAuthFilter(propriedades.seguranca().mcpApiKey(),
                        "api-atendimentos", PAPEL_SERVICO_ATENDIMENTO), AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain cadeiaPadrao(HttpSecurity http, RespostaSegurancaProblema resposta) throws Exception {
        http.authorizeHttpRequests(a -> {
            a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            a.anyRequest().authenticated();
        });
        return comBase(http, resposta).build();
    }

    private static HttpSecurity comBase(HttpSecurity http, RespostaSegurancaProblema resposta) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(resposta).accessDeniedHandler(resposta));
    }
}
