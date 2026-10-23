package br.com.coti.atendimentos.infraestrutura.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.atendimentos.suporte.ChavesTeste;
import br.com.coti.atendimentos.suporte.TesteWeb;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** A api-atendimentos usa só chat (Artigo 6): o modelo de embedding não deve existir nesta aplicação. */
@TesteWeb
class ConfiguracaoSpringAiTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private Environment ambiente;

    @Test
    void deveTerOModeloDeChatEnaoOModeloDeEmbedding() {
        assertThat(contexto.getBeanNamesForType(ChatModel.class)).isNotEmpty();
        assertThat(contexto.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
    }

    @Test
    void deveUsarOsParametrosDeChatDaArquitetura() {
        assertThat(ambiente.getProperty("spring.ai.openai.chat.model")).isEqualTo(ChavesTeste.MODELO_CHAT);
        assertThat(ambiente.getProperty("spring.ai.openai.chat.temperature")).isEqualTo("0.0");
        assertThat(ambiente.getProperty("spring.ai.openai.chat.max-tokens")).isEqualTo("600");
        assertThat(ambiente.getProperty("spring.ai.openai.max-retries")).isEqualTo("0");
        assertThat(ambiente.getProperty("spring.ai.openai.timeout")).isEqualTo("20s");
    }

    @Test
    void deveExporAsPropriedadesProprias() {
        PropriedadesAtendimentos propriedades = contexto.getBean(PropriedadesAtendimentos.class);
        assertThat(propriedades.web().limiteCorpoBytes()).isEqualTo(262_144);
        assertThat(propriedades.documentacaoPublica()).isFalse();
        assertThat(propriedades.toString()).doesNotContain(ChavesTeste.OPENAI).doesNotContain(ChavesTeste.MONITORAMENTO);
    }
}
