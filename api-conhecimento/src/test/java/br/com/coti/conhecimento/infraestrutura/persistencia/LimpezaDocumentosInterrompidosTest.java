package br.com.coti.conhecimento.infraestrutura.persistencia;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.coti.conhecimento.aplicacao.RecolherDocumentosInterrompidosService;
import org.junit.jupiter.api.Test;

class LimpezaDocumentosInterrompidosTest {

    private final RecolherDocumentosInterrompidosService recolhedor = mock(RecolherDocumentosInterrompidosService.class);
    private final LimpezaDocumentosInterrompidos limpeza = new LimpezaDocumentosInterrompidos(recolhedor);

    @Test
    void naSubidaChamaORecolhedor() {
        when(recolhedor.recolher()).thenReturn(2);

        limpeza.run(null);

        verify(recolhedor).recolher();
    }

    @Test
    void falhaNaLimpezaNaoImpedeAAplicacaoDeSubir() {
        when(recolhedor.recolher()).thenThrow(new IllegalStateException("banco indisponível"));

        assertThatCode(() -> limpeza.run(null)).doesNotThrowAnyException();
    }
}
