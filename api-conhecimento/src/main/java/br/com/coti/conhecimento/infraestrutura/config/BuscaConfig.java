package br.com.coti.conhecimento.infraestrutura.config;

import br.com.coti.conhecimento.aplicacao.BuscarConhecimentoService;
import br.com.coti.conhecimento.aplicacao.porta.BuscaVetorialPort;
import br.com.coti.conhecimento.dominio.LimitesBusca;
import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta o caso de uso de busca (RF04) a partir da configuração. */
@Configuration
public class BuscaConfig {

    @Bean
    LimitesBusca limitesBusca(PropriedadesConhecimento propriedades) {
        return propriedades.busca().paraDominio();
    }

    @Bean
    BuscarConhecimentoService buscarConhecimentoService(BuscaVetorialPort busca, ModeloEmbedding modelo,
                                                        LimitesBusca limites, Clock relogio) {
        return new BuscarConhecimentoService(busca, modelo, limites, relogio);
    }
}
