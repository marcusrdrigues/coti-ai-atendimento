package br.com.coti.conhecimento.dominio;

/** Nome do modelo de embedding em uso; gravado em cada documento e em cada trecho (RN03.1). */
public record ModeloEmbedding(String nome) {

    public ModeloEmbedding {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome do modelo de embedding é obrigatório");
        }
    }
}
