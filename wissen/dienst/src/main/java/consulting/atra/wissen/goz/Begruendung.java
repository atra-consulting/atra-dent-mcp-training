package consulting.atra.wissen.goz;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Begruendung {

    private Begruendung() {
    }

    static String included(String nummer, String leistungsbereichName, String tarifname, int quote,
                            Leistungsgrenzen grenzen, String hinweis) {
        String restrictions = restrictions(grenzen);
        String kern = "Die Gebührennummer %s fällt in den Leistungsbereich %s; dieser ist in %s zu %d Prozent versichert"
                .formatted(nummer, leistungsbereichName, tarifname, quote)
                + (restrictions.isEmpty() ? "." : ", " + restrictions + ".");
        return withHint(kern, hinweis);
    }

    static String notIncluded(String nummer, String leistungsbereichName, String tarifname, String hinweis) {
        String kern = ("Die Gebührennummer %s fällt in den Leistungsbereich %s; dieser ist in %s "
                + "nicht versichert.").formatted(nummer, leistungsbereichName, tarifname);
        return withHint(kern, hinweis);
    }

    static String notDeterminable(String nummer, String tarifname, String hinweis) {
        String kern = ("Die Gebührennummer %s steht in der Anlage 1 zur GOZ, gehört aber keinem "
                + "Leistungsbereich von atra.dent an; zu klären ist, zu welcher Behandlung die "
                + "Position gehört, denn erst daraus ergibt sich, ob %s dafür leistet.")
                .formatted(nummer, tarifname);
        return withHint(kern, hinweis);
    }

    static String notDeterminableGoae(String nummer, String tarifname, String hinweis) {
        String kern = ("Die Gebührennummer %s ist eine Leistung nach der Gebührenordnung für "
                + "Ärzte, die der Zahnarzt nach Paragraf 6 Absatz 2 GOZ berechnen darf; sie "
                + "gehört keinem Leistungsbereich von atra.dent an. Zu klären ist, zu welcher "
                + "Behandlung die Position gehört, denn erst daraus ergibt sich, ob %s dafür "
                + "leistet.").formatted(nummer, tarifname);
        return withHint(kern, hinweis);
    }

    static String alsoInGoae(String nummer, String bezeichnungGoae) {
        return ("Die Nummer %s gibt es auch in der Gebührenordnung für Ärzte, dort als \"%s\"; "
                + "welche der beiden gemeint ist, weist nicht jede Rechnung aus. Dieser Befund "
                + "geht von der GOZ aus.").formatted(nummer, bezeichnungGoae);
    }

    static String withPostscript(String sentence, String nachsatz) {
        return nachsatz == null || nachsatz.isBlank() ? sentence : sentence + " " + nachsatz;
    }

    static String unknown(String nummer, String tarifname) {
        return ("Die Gebührennummer %s steht nicht in der Anlage 1 zur GOZ; zu klären ist, welche "
                + "Leistung abgerechnet wurde, denn häufig steht dahinter eine Leistung nach der "
                + "Gebührenordnung für Ärzte wie Vollnarkose, Sedierung oder eine Röntgenaufnahme, "
                + "über die %s getrennt entscheidet.").formatted(nummer, tarifname);
    }

    static String restrictions(Leistungsgrenzen grenzen) {
        List<String> parts = new ArrayList<>();
        if (grenzen.limitProJahr() != null) {
            parts.add("begrenzt auf " + amount(grenzen.limitProJahr()) + " je Versicherungsjahr");
        }
        if (grenzen.limitGesamt() != null) {
            parts.add("begrenzt auf " + amount(grenzen.limitGesamt())
                    + " über die gesamte Vertragslaufzeit");
        }
        if (grenzen.faelleProJahr() != null) {
            parts.add("höchstens " + count(grenzen.faelleProJahr(), "Fall", "Fälle")
                    + " je Versicherungsjahr");
        }
        if (grenzen.maxFaelle() != null) {
            String cases = "höchstens " + count(grenzen.maxFaelle(), "Fall", "Fälle");
            if (grenzen.zeitraumJahre() != null) {
                cases += " in " + count(grenzen.zeitraumJahre(), "Jahr", "Jahren");
            }
            parts.add(cases);
        } else if (grenzen.zeitraumJahre() != null) {
            parts.add("bezogen auf " + count(grenzen.zeitraumJahre(), "Jahr", "Jahre"));
        }
        if (grenzen.wartezeitMonate() != null) {
            parts.add("nach einer Wartezeit von "
                    + count(grenzen.wartezeitMonate(), "Monat", "Monaten"));
        }
        if (grenzen.bedingung() != null) {
            parts.add("bei " + grenzen.bedingung());
        }
        return join(parts);
    }

    private static String withHint(String kern, String hinweis) {
        if (hinweis == null || hinweis.isBlank()) {
            return kern;
        }
        String sentence = hinweis.strip().replaceAll("\\s+", " ");
        if (!sentence.endsWith(".") && !sentence.endsWith("!") && !sentence.endsWith("?")) {
            sentence += ".";
        }
        return kern + " " + sentence;
    }

    private static String join(List<String> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " und " + parts.getLast();
    }

    private static String count(int value, String einzahl, String mehrzahl) {
        return value + " " + (value == 1 ? einzahl : mehrzahl);
    }

    private static String amount(BigDecimal value) {
        return String.format(Locale.GERMANY, "%,.2f Euro", value);
    }
}
