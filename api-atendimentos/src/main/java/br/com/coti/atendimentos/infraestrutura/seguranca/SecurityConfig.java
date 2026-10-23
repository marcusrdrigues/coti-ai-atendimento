package br.com.coti.atendimentos.infraestrutura.seguranca;

import br.com.coti.atendimentos.infraestrutura.config.PropriedadesAtendimentos;
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
 * Segurança da api-atendimentos: sem sessão, sem CSRF (API sem cookies), CORS desligado e negação por padrão.
 *
 * <ul>
 *   <li>{@code POST /api/atendimentos/perguntar}: público, protegido por limites de tamanho e de requisições
 *       (Fase 8); não devolve dado individual;</li>
 *   <li>{@code /actuator/**} e documentação: chave de monitoramento (health e info públicos);</li>
 *   <li>qualquer outra rota: exige autenticação, que nenhuma credencial satisfaz (401).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String PAPEL_MONITORAMENTO = "MONITORAMENTO";
    static final String ROTA_PERGUNTAR = "/api/atendimentos/perguntar";

    @Bean
    @Order(1)
    SecurityFilterChain cadeiaMonitoramento(HttpSecurity http, PropriedadesAtendimentos propriedades,
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
    SecurityFilterChain cadeiaPadrao(HttpSecurity http, RespostaSegurancaProblema resposta) throws Exception {
        http.authorizeHttpRequests(a -> {
            a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            // qualquer método: um GET na rota pública responde 405 do contrato, não 401
            a.requestMatchers(ROTA_PERGUNTAR).permitAll();
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
