package br.com.coti.conhecimento.infraestrutura.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.conhecimento.suporte.TesteWeb;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** A api-conhecimento usa só embeddings (Artigo 6): o modelo de chat não deve existir nesta aplicação. */
@TesteWeb
class ConfiguracaoSpringAiTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private Environment ambiente;

    @Test
    void deveTerOModeloDeEmbeddingEnaoOModeloDeChat() {
        assertThat(contexto.getBeanNamesForType(EmbeddingModel.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(ChatModel.class)).isEmpty();
    }

    @Test
    void deveUsarOModeloEADimensaoDaArquitetura() {
        assertThat(ambiente.getProperty("spring.ai.openai.embedding.model")).isEqualTo("text-embedding-3-small");
        assertThat(ambiente.getProperty("spring.ai.openai.embedding.dimensions")).isEqualTo("1536");
        assertThat(ambiente.getProperty("spring.ai.openai.max-retries")).isEqualTo("0");
    }

    @Test
    void deveExporAsPropriedadesProprias() {
        PropriedadesConhecimento propriedades = contexto.getBean(PropriedadesConhecimento.class);
        assertThat(propriedades.web().limiteCorpoBytes()).isEqualTo(262_144);
        assertThat(propriedades.documentacaoPublica()).isFalse();
        assertThat(propriedades.toString()).doesNotContain("chave-");
    }
}
