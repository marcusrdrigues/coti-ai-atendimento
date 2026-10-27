package br.com.coti.conhecimento.suporte;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * Como {@link TesteWeb}, mas sobe o servidor HTTP de verdade em porta aleatória, para um cliente MCP real falar
 * com o endpoint {@code /mcp} por Streamable HTTP. Exige o Docker ativo (PostgreSQL em Testcontainers).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "CONHECIMENTO_ADMIN_API_KEY=" + ChavesTeste.ADMIN,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "CONHECIMENTO_MCP_API_KEY=" + ChavesTeste.MCP
})
@ContextConfiguration(initializers = PostgresContainerConfig.class)
@Import(EmbeddingFalsoConfig.class)
public @interface TesteServidor {
}
