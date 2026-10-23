package br.com.coti.conhecimento.suporte;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * Teste da aplicação completa com MockMvc e credenciais falsas. Nenhuma chamada à OpenAI é feita:
 * as chaves existem só para satisfazer a configuração obrigatória.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "OPENAI_API_KEY=" + ChavesTeste.OPENAI,
        "CONHECIMENTO_ADMIN_API_KEY=" + ChavesTeste.ADMIN,
        "MONITORAMENTO_API_KEY=" + ChavesTeste.MONITORAMENTO
})
@AutoConfigureMockMvc
public @interface TesteWeb {
}
