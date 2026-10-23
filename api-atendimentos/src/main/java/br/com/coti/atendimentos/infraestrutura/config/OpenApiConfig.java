package br.com.coti.atendimentos.infraestrutura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadados da especificação OpenAPI da api-atendimentos (endpoint público de perguntas). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApiAtendimentos() {
        return new OpenAPI()
                .info(new Info()
                        .title("COTI AI Atendimento — api-atendimentos")
                        .version("1.0.0")
                        .description("Recebe perguntas de alunos, consulta a base de conhecimento por MCP e "
                                + "responde com base nas evidências recuperadas. Contrato em "
                                + "specs/06-api-contracts.md."));
    }
}
