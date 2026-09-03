package consulting.atra.wissen.beratung;

import consulting.atra.produktmodell.Leistung;

import java.math.BigDecimal;
import java.util.Objects;

final class CoverageComparison {

    private CoverageComparison() {
    }

    static int comparisons(Leistung a, Leistung b) {
        int value = Integer.compare(quote(a), quote(b));
        if (value != 0) {
            return value;
        }
        value = higherIsBetter(a.limitProJahr(), b.limitProJahr());
        if (value != 0) {
            return value;
        }
        value = higherIsBetter(a.limitGesamt(), b.limitGesamt());
        if (value != 0) {
            return value;
        }
        value = moreIsBetter(a.faelleProJahr(), b.faelleProJahr());
        if (value != 0) {
            return value;
        }
        if (Objects.equals(a.zeitraumJahre(), b.zeitraumJahre())) {
            value = moreIsBetter(a.maxFaelle(), b.maxFaelle());
            if (value != 0) {
                return value;
            }
        }
        value = lessIsBetter(a.wartezeitMonate(), b.wartezeitMonate());
        if (value != 0) {
            return value;
        }
        return Boolean.compare(a.bedingung() == null, b.bedingung() == null);
    }

    private static int quote(Leistung leistung) {
        if (!leistung.versichert()) {
            return 0;
        }
        if (leistung.quote() == null) {
            throw new IllegalStateException(
                    "Ein versicherter Leistungsbereich ohne Quote laesst sich nicht vergleichen");
        }
        return leistung.quote();
    }

    private static int higherIsBetter(BigDecimal a, BigDecimal b) {
        if (Objects.equals(a, b)) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }

    private static int moreIsBetter(Integer a, Integer b) {
        if (Objects.equals(a, b)) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return Integer.compare(a, b);
    }

    private static int lessIsBetter(Integer a, Integer b) {
        if (Objects.equals(a, b)) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return Integer.compare(b, a);
    }
}
