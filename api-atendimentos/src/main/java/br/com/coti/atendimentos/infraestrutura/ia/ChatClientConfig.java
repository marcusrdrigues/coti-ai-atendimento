package br.com.coti.atendimentos.infraestrutura.ia;

import br.com.coti.atendimentos.infraestrutura.config.PropriedadesAtendimentos;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Monta o {@link ChatClient} do atendimento e o adaptador que o usa. O cliente é criado a partir do builder
 * autoconfigurado, SEM ferramentas ({@code defaultTools}, {@code defaultToolCallbacks}): o modelo não pesquisa,
 * não chama MCP e não age (Artigos 10 e 11). Modelo, temperatura e limite de tokens vêm de
 * {@code spring.ai.openai.chat.*}. Os prompts são arquivos versionados em {@code prompts/}.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    ChatClient chatClientAtendimento(ChatClient.Builder construtor) {
        return construtor.build();
    }

    @Bean
    OpenAiGeradorRespostaAdapter openAiGeradorRespostaAdapter(ChatClient chatClientAtendimento,
                                                              PropriedadesAtendimentos propriedades) {
        String versao = propriedades.prompt().versao();
        PropriedadesAtendimentos.Resiliencia r = propriedades.resiliencia();
        return new OpenAiGeradorRespostaAdapter(chatClientAtendimento,
                new ClassPathResource("prompts/responder-pergunta.system." + versao + ".st"),
                new ClassPathResource("prompts/responder-pergunta.user." + versao + ".st"),
                new OpenAiGeradorRespostaAdapter.Politica(r.chatTempoLimite(), r.chatNovasTentativas(),
                        r.atrasoInicial(), r.multiplicador(), r.atrasoMaximo(), r.jitter()));
    }
}
