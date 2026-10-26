package br.com.coti.conhecimento.dominio.excecao;

/** O documento geraria mais trechos que o máximo permitido; é recusado antes de gravar qualquer coisa. */
public class DocumentoGrandeDemaisException extends RuntimeException {

    private final int maximoTrechos;

    public DocumentoGrandeDemaisException(int maximoTrechos) {
        super("O documento excede o máximo de " + maximoTrechos + " trechos");
        this.maximoTrechos = maximoTrechos;
    }

    public int maximoTrechos() {
        return maximoTrechos;
    }
}
