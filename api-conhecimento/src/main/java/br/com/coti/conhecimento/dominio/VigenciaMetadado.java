package br.com.coti.conhecimento.dominio;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Vigência de um trecho como inteiro {@code AAAAMMDD} (ADR-08): o filtro da busca é {@code vigenteAte >= hoje},
 * sem depender de {@code isNull} nem de comparação de datas em JSONPath. Sem vigência vale {@code 99991231}.
 */
public final class VigenciaMetadado {

    public static final int SEM_VIGENCIA = 99_991_231;

    private VigenciaMetadado() {
    }

    public static int paraMetadado(LocalDate vigenteAte) {
        if (vigenteAte == null) {
            return SEM_VIGENCIA;
        }
        return vigenteAte.getYear() * 10_000 + vigenteAte.getMonthValue() * 100 + vigenteAte.getDayOfMonth();
    }

    /** Volta para a data; {@code null} quando o metadado indica que não há vigência. */
    public static LocalDate dataDe(int metadado) {
        if (metadado == SEM_VIGENCIA) {
            return null;
        }
        return LocalDate.of(metadado / 10_000, (metadado / 100) % 100, metadado % 100);
    }

    /** Valor de "hoje" para comparar com o metadado. */
    public static int hoje(Clock relogio) {
        return paraMetadado(LocalDate.now(relogio));
    }

    public static boolean estaVigente(int metadado, int hoje) {
        return metadado >= hoje;
    }
}
