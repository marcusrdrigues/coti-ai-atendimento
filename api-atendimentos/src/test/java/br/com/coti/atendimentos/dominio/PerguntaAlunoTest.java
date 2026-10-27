package br.com.coti.atendimentos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.atendimentos.dominio.excecao.PerguntaInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PerguntaAlunoTest {

    @Test
    void removeEspacosNasPontas() {
        assertThat(new PerguntaAluno("  Qual o valor do curso?  \n").texto()).isEqualTo("Qual o valor do curso?");
    }

    @Test
    void aceitaOsLimitesDe3E1000Caracteres() {
        assertThat(new PerguntaAluno("abc").texto()).hasSize(3);
        assertThat(new PerguntaAluno("a".repeat(1_000)).texto()).hasSize(1_000);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ab", "  ab  "})
    void recusaPerguntaCurtaOuVazia(String texto) {
        assertThatThrownBy(() -> new PerguntaAluno(texto)).isInstanceOf(PerguntaInvalidaException.class);
    }

    @Test
    void recusaMaisDeMilCaracteresSemEcoDoValor() {
        String longa = "z".repeat(1_001);

        assertThatThrownBy(() -> new PerguntaAluno(longa)).isInstanceOf(PerguntaInvalidaException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("zzzz"));
    }

    @Test
    void oTextoNuncaApareceNoToString() {
        assertThat(new PerguntaAluno("meu cpf e secreto").toString()).doesNotContain("cpf", "secreto");
    }
}
