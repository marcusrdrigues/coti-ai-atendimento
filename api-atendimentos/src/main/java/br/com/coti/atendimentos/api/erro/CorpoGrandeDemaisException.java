package br.com.coti.atendimentos.api.erro;

import java.io.IOException;

/** Lançada ao ler um corpo sem Content-Length que ultrapassa o limite configurado. */
public class CorpoGrandeDemaisException extends IOException {

    public CorpoGrandeDemaisException(long limiteBytes) {
        super("Corpo da requisição acima do limite de " + limiteBytes + " bytes");
    }
}
