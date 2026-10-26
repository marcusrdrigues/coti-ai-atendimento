package br.com.coti.conhecimento.infraestrutura.config;

import br.com.coti.conhecimento.aplicacao.IndexarDocumentoService;
import br.com.coti.conhecimento.aplicacao.PoliticaNovasTentativas;
import br.com.coti.conhecimento.aplicacao.RecolherDocumentosInterrompidosService;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioDocumentoPort;
import java.time.Clock;
import java.time.Duration;
import br.com.coti.conhecimento.aplicacao.porta.RepositorioVetorialPort;
import br.com.coti.conhecimento.dominio.DivisorTexto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta, a partir da configuração, as peças da indexação (divisor, novas tentativas e gravação em lotes). */
@Configuration
public class IndexacaoConfig {

    /** Tempo máximo que um cadastro leva; acima disso, um documento PROCESSANDO é considerado interrompido. */
    static final Duration TEMPO_MAXIMO_PROCESSANDO = Duration.ofMinutes(10);

    @Bean
    DivisorTexto divisorTexto(PropriedadesConhecimento propriedades) {
        return new DivisorTexto(propriedades.divisao().paraDominio());
    }

    @Bean
    PoliticaNovasTentativas politicaNovasTentativas(PropriedadesConhecimento propriedades) {
        PropriedadesConhecimento.Embeddings e = propriedades.embeddings();
        return new PoliticaNovasTentativas(e.novasTentativas(), e.atrasoInicial(), e.multiplicador(),
                e.atrasoMaximo(), e.jitter());
    }

    @Bean
    RecolherDocumentosInterrompidosService recolherDocumentosInterrompidosService(
            RepositorioDocumentoPort documentos, RepositorioVetorialPort vetorial, Clock relogio) {
        return new RecolherDocumentosInterrompidosService(documentos, vetorial, TEMPO_MAXIMO_PROCESSANDO, relogio);
    }

    @Bean
    IndexarDocumentoService indexarDocumentoService(RepositorioVetorialPort vetorial,
                                                     PoliticaNovasTentativas novasTentativas,
                                                     PropriedadesConhecimento propriedades) {
        return new IndexarDocumentoService(vetorial, novasTentativas, propriedades.embeddings().tamanhoLote());
    }
}
