package br.com.coti.conhecimento.infraestrutura.config;

import br.com.coti.conhecimento.dominio.ModeloEmbedding;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans de infraestrutura compartilhados pelos casos de uso. */
@Configuration
public class ConhecimentoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ModeloEmbedding modeloEmbedding(PropriedadesConhecimento propriedades) {
        return new ModeloEmbedding(propriedades.embedding().modelo());
    }
}
