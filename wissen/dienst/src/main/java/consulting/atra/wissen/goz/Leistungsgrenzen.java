package consulting.atra.wissen.goz;

import consulting.atra.produktmodell.Leistung;

import java.math.BigDecimal;
import java.util.Objects;

public record Leistungsgrenzen(
        BigDecimal limitProJahr,
        BigDecimal limitGesamt,
        Integer faelleProJahr,
        Integer maxFaelle,
        Integer zeitraumJahre,
        Integer wartezeitMonate,
        String bedingung) {

    private static final Leistungsgrenzen NONE =
            new Leistungsgrenzen(null, null, null, null, null, null, null);

    public static Leistungsgrenzen none() {
        return NONE;
    }

    public static Leistungsgrenzen aus(Leistung leistung) {
        Objects.requireNonNull(leistung, "leistung");
        if (!leistung.hasLimits()) {
            return NONE;
        }
        return new Leistungsgrenzen(
                leistung.limitProJahr(),
                leistung.limitGesamt(),
                leistung.faelleProJahr(),
                leistung.maxFaelle(),
                leistung.zeitraumJahre(),
                leistung.wartezeitMonate(),
                leistung.bedingung());
    }

    public boolean istLeer() {
        return limitProJahr == null
                && limitGesamt == null
                && faelleProJahr == null
                && maxFaelle == null
                && zeitraumJahre == null
                && wartezeitMonate == null
                && bedingung == null;
    }
}
