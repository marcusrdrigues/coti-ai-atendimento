package br.com.coti.atendimentos.dominio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saída do modelo como ele a devolveu (contrato, seção 6.4), ainda NÃO validada: as referências são os rótulos
 * {@code T<n>} que o modelo disse ter usado. Só vira resposta ao aluno depois de passar pelo validador e de as
 * fontes serem derivadas do resultado MCP.
 */
public record RespostaGerada(String resposta, boolean conhecimentoSuficiente, List<String> referencias) {

    public RespostaGerada {
        // Cópia imutável que aceita elemento nulo: o JSON do modelo pode trazer {@code [null]} e quem reprova é o
        // validador, não uma NullPointerException aqui.
        referencias = referencias == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(referencias));
    }

    /** O texto do modelo não vai para log. */
    @Override
    public String toString() {
        return "RespostaGerada[conhecimentoSuficiente=" + conhecimentoSuficiente + ", referencias=" + referencias + "]";
    }
}
