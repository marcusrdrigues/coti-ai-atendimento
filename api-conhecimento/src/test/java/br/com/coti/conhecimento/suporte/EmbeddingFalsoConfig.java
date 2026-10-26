package br.com.coti.conhecimento.suporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Substitui o modelo de embedding da OpenAI por {@link EmbeddingModelFalso} em todos os testes web. */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddingFalsoConfig {

    @Bean
    @Primary
    EmbeddingModelFalso embeddingModelFalso() {
        return new EmbeddingModelFalso();
    }
}
