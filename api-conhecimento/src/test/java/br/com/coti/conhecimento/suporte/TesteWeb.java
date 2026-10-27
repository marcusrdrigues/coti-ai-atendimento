package br.com.coti.conhecimento.suporte;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;

/**
 * Teste da aplicação completa com MockMvc, credenciais falsas e PostgreSQL real em Testcontainers (exige o
 * Docker ativo). Nenhuma chamada à OpenAI é feita: as chaves só satisfazem a configuração obrigatória.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "CONHECIMENTO_ADMIN_API_KEY=" + ChavesTeste.ADMIN,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO,
        "CONHECIMENTO_MCP_API_KEY=" + ChavesTeste.MCP,
        "coti.conhecimento.limites-requisicao.cadastro-por-minuto=100000",
        "coti.conhecimento.limites-requisicao.diagnostico-por-minuto=100000"
})
@ContextConfiguration(initializers = PostgresContainerConfig.class)
@Import(EmbeddingFalsoConfig.class)
@AutoConfigureMockMvc
public @interface TesteWeb {
}
