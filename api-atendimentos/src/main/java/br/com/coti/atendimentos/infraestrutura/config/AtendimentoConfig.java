package br.com.coti.atendimentos.infraestrutura.config;

import br.com.coti.atendimentos.aplicacao.ResponderPerguntaService;
import br.com.coti.atendimentos.aplicacao.porta.BaseConhecimentoPort;
import br.com.coti.atendimentos.aplicacao.porta.GeradorRespostaPort;
import br.com.coti.atendimentos.aplicacao.porta.MascaradorPort;
import br.com.coti.atendimentos.infraestrutura.mcp.BaseConhecimentoMcpAdapter;
import br.com.coti.atendimentos.infraestrutura.mcp.ConhecimentoMcpHealthIndicator;
import br.com.coti.atendimentos.infraestrutura.mcp.McpCabecalhosCustomizer;
import br.com.coti.atendimentos.infraestrutura.mcp.McpContextoTraceCustomizer;
import br.com.coti.atendimentos.infraestrutura.mcp.ValidadorResultadoBusca;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.time.Clock;
import java.util.List;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta, a partir da configuração, o cliente MCP, o adaptador de busca e o caso de uso do atendimento. */
@Configuration
public class AtendimentoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemDefaultZone();
    }

    @Bean
    McpSyncHttpClientRequestCustomizer mcpCabecalhosCustomizer(PropriedadesAtendimentos propriedades) {
        return new McpCabecalhosCustomizer(propriedades.seguranca().mcpApiKey());
    }

    @Bean
    McpClientCustomizer<McpClient.SyncSpec> mcpContextoTraceCustomizer() {
        return new McpContextoTraceCustomizer();
    }

    @Bean
    ValidadorResultadoBusca validadorResultadoBusca() {
        return new ValidadorResultadoBusca();
    }

    @Bean
    BaseConhecimentoMcpAdapter baseConhecimentoMcpAdapter(List<McpSyncClient> clientes, ValidadorResultadoBusca validador,
                                                          PropriedadesAtendimentos propriedades) {
        PropriedadesAtendimentos.Resiliencia r = propriedades.resiliencia();
        return new BaseConhecimentoMcpAdapter(clientes, validador, new BaseConhecimentoMcpAdapter.Politica(
                r.mcpTempoLimite(), r.mcpNovasTentativas(), r.atrasoInicial(), r.multiplicador(), r.atrasoMaximo(),
                r.jitter()));
    }

    @Bean
    ConhecimentoMcpHealthIndicator conhecimentoMcp(List<McpSyncClient> clientes, PropriedadesAtendimentos propriedades,
                                                   Clock relogio) {
        PropriedadesAtendimentos.Resiliencia r = propriedades.resiliencia();
        return new ConhecimentoMcpHealthIndicator(clientes, r.saudeTempoLimite(), r.saudeValidade(), relogio);
    }

    @Bean
    ResponderPerguntaService responderPerguntaService(MascaradorPort mascarador, BaseConhecimentoPort base,
                                                      GeradorRespostaPort gerador, PropriedadesAtendimentos propriedades,
                                                      Clock relogio) {
        PropriedadesAtendimentos.Mensagens m = propriedades.mensagens();
        return new ResponderPerguntaService(mascarador, base, gerador,
                new ResponderPerguntaService.MensagensSemEvidencia(m.semEvidencia(), m.canalHumano()),
                propriedades.busca().limite(), propriedades.resiliencia().prazoTotal(), relogio);
    }
}
