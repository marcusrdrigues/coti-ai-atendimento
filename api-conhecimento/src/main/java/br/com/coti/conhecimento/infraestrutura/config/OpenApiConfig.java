package br.com.coti.conhecimento.infraestrutura.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados da especificação OpenAPI da api-conhecimento (endpoints REST; a ferramenta MCP fica no contrato). */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA_CHAVE_ADMIN = "chaveAdministrativa";

    @Bean
    OpenAPI openApiConhecimento() {
        return new OpenAPI()
                .info(new Info()
                        .title("COTI AI Atendimento — api-conhecimento")
                        .version("1.0.0")
                        .description("Cadastro e consulta da base de conhecimento. "
                                + "A busca semântica para o atendimento é a ferramenta MCP buscar_conhecimento, "
                                + "descrita em specs/06-api-contracts.md."))
                .components(new Components().addSecuritySchemes(ESQUEMA_CHAVE_ADMIN,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("Chave administrativa (CONHECIMENTO_ADMIN_API_KEY)")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_CHAVE_ADMIN));
    }
}
