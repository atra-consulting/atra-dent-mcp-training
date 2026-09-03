package consulting.atra.produktmodell;

import java.math.BigDecimal;

public record Leistung(
        boolean versichert,
        Integer quote,
        BigDecimal limitProJahr,
        BigDecimal limitGesamt,
        Integer faelleProJahr,
        Integer maxFaelle,
        Integer zeitraumJahre,
        Integer wartezeitMonate,
        String bedingung) {

    public boolean hasLimits() {
        return limitProJahr != null
                || limitGesamt != null
                || faelleProJahr != null
                || maxFaelle != null
                || zeitraumJahre != null
                || wartezeitMonate != null
                || bedingung != null;
    }
}
