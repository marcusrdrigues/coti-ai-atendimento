package br.com.coti.conhecimento.infraestrutura.persistencia;

import br.com.coti.conhecimento.aplicacao.RecolherDocumentosInterrompidosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na subida, recolhe cadastros interrompidos (documentos {@code PROCESSANDO} há mais de 10 minutos). Uma falha
 * aqui é registrada mas não impede a aplicação de subir: a limpeza roda de novo na próxima subida.
 */
@Component
public final class LimpezaDocumentosInterrompidos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LimpezaDocumentosInterrompidos.class);

    private final RecolherDocumentosInterrompidosService recolhedor;

    public LimpezaDocumentosInterrompidos(RecolherDocumentosInterrompidosService recolhedor) {
        this.recolhedor = recolhedor;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            int recolhidos = recolhedor.recolher();
            if (recolhidos > 0) {
                log.warn("Limpeza na subida: {} cadastro(s) interrompido(s) marcado(s) como ERRO_PROCESSAMENTO.", recolhidos);
            }
        } catch (RuntimeException falha) {
            log.error("A limpeza de cadastros interrompidos falhou; será tentada na próxima subida.", falha);
        }
    }
}
