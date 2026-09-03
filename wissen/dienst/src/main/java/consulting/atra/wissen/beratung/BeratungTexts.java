package consulting.atra.wissen.beratung;

import consulting.atra.produktmodell.Leistung;
import consulting.atra.produktmodell.Tarif;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class BeratungTexts {

    private BeratungTexts() {
    }

    static String sentence(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.strip().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    static List<String> sentences(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(BeratungTexts::sentence).filter(Objects::nonNull).toList();
    }


    static String focusAreaReason(String leistungsbereichName, Leistung leistung) {
        return "%s deckt dieser Tarif von den verbliebenen am weitesten ab: %s."
                .formatted(leistungsbereichName, promise(leistung));
    }

    static String missingZaehneReason(String ausnahmetext) {
        return "Für bei Vertragsschluss fehlende Zähne sieht dieser Tarif eine Exception vor: "
                + withoutPeriod(ausnahmetext) + ".";
    }

    static String wartezeitGrund(Tarif tarif) {
        return "Die Wartezeit von %s entfällt wegen des lückenlosen Vorversicherungsschutzes."
                .formatted(count(tarif.wartezeitMonate(), "Monat", "Monaten"));
    }


    static String recommendationReason(Tarif tarif, String kurzformel, List<NeedReason> gruende,
                                         int reihenfolgeplatz, BeratungRequest anliegen,
                                         List<String> deckung,
                                         boolean ersatzFehlenderZaehneOffen) {
        List<String> parts = new ArrayList<>();
        parts.add("%s — %s.".formatted(tarif.anzeigename(), withoutPeriod(kurzformel)));

        if (gruende.isEmpty()) {
            parts.add(("Keine der genannten Angaben unterscheidet diesen Tarif von den übrigen; er "
                    + "steht auf Platz %d der Empfehlungsreihenfolge des Leitfadens, die nur bei "
                    + "gleichwertiger Eignung gilt.").formatted(reihenfolgeplatz));
        } else {
            parts.add("Der Bedarf spricht für diesen Tarif: "
                    + String.join(" ", gruende.stream().map(NeedReason::text).toList()));
        }

        if (!deckung.isEmpty()) {
            parts.add("Zum genannten Bedarf: " + String.join("; ", deckung) + ".");
        }

        parts.add(selbstbehalt(tarif));
        parts.add(wartezeit(tarif, anliegen.vorversicherung()));

        if (tarif.jahreshoechstgrenze() != null) {
            parts.add("Die Jahreshöchstgrenze beträgt " + amount(tarif.jahreshoechstgrenze()) + ".");
        }
        if (ersatzFehlenderZaehneOffen) {
            parts.add("Für den Ersatz der bei Vertragsschluss fehlenden Zähne leistet dieser Tarif "
                    + "nicht.");
        }
        return String.join(" ", parts);
    }

    static String exclusionByAge(Tarif tarif, int alter) {
        return ("%s ist mit %s nicht abschließbar: der Tarif kann nur im Eintrittsalter von %d bis "
                + "%d Jahren abgeschlossen werden.").formatted(tarif.anzeigename(),
                count(alter, "Jahr", "Jahren"), tarif.eintrittsalter().von(),
                tarif.eintrittsalter().bis());
    }

    static String exclusionByArea(Tarif tarif, String leistungsbereichName) {
        return ("%s versichert %s nicht; der genannte Behandlungsschwerpunkt liegt damit außerhalb "
                + "des Tarifs.").formatted(tarif.anzeigename(), leistungsbereichName);
    }


    static String summaryNeed(String anzeigename, List<ExcludedTarif> ausgeschlossen) {
        return ("Der Bedarf bestimmt die Auswahl: %s steht vorn, weil ihn die genannten Angaben "
                + "gegenüber den übrigen Tarifen auszeichnen. Die Empfehlungsreihenfolge des "
                + "Leitfadens entscheidet hier nur den Gleichstand.").formatted(anzeigename)
                + exclusionSuffix(ausgeschlossen);
    }

    static String summaryOrder(String anzeigename,
                                             List<ExcludedTarif> ausgeschlossen) {
        return ("Keine der genannten Angaben unterscheidet die verbliebenen Tarife fachlich; "
                + "deshalb gilt die Empfehlungsreihenfolge des Leitfadens, und %s steht vorn. Sie "
                + "gilt nur bei gleichwertiger Eignung — ergibt die Bedarfsanalyse etwas anderes, "
                + "geht der Bedarf vor.").formatted(anzeigename)
                + exclusionSuffix(ausgeschlossen);
    }

    static String summaryNoTarif(List<ExcludedTarif> ausgeschlossen) {
        return ("Kein Tarif von atra.dent kommt für dieses Anliegen in Betracht: alle %s scheiden "
                + "an harten Kriterien aus (%s). Die Ausschlussgründe stehen einzeln in diesem "
                + "Ergebnis und sind offen zu nennen.").formatted(
                count(ausgeschlossen.size(), "Tarif", "Tarife"), names(ausgeschlossen));
    }

    private static String exclusionSuffix(List<ExcludedTarif> ausgeschlossen) {
        if (ausgeschlossen.isEmpty()) {
            return "";
        }
        return " An harten Kriterien scheiden %s aus: %s.".formatted(
                count(ausgeschlossen.size(), "Tarif", "Tarife"), names(ausgeschlossen));
    }

    private static String names(List<ExcludedTarif> ausgeschlossen) {
        return join(ausgeschlossen.stream().map(ExcludedTarif::anzeigename).toList());
    }


    static String hintAngeraten(String ausschlusstext) {
        return ("Es ist bereits eine Behandlung angeraten oder begonnen. Dafür leistet kein Tarif "
                + "von atra.dent: \"%s\" ist in allen Tarifen ausgeschlossen. Das gehört vor die "
                + "Tarifempfehlung und nicht auf Nachfrage; wer bereits einen Heil- und Kostenplan "
                + "hat, muss wissen, dass dafür nicht geleistet wird.")
                .formatted(withoutPeriod(ausschlusstext));
    }

    static String hintAngeratenOpen(String ausschlusstext) {
        return ("Ob bereits eine Behandlung angeraten oder begonnen wurde, ist nicht erhoben. Die "
                + "Frage ist zu stellen, bevor ein Tarif empfohlen wird: \"%s\" ist in allen "
                + "Tarifen ausgeschlossen.").formatted(withoutPeriod(ausschlusstext));
    }

    static String hintMissingZaehne(int count, List<String> mitAusnahme,
                                        List<String> ohneAusnahme, String ausnahmetext) {
        StringBuilder text = new StringBuilder("Bei Vertragsschluss fehlen ")
                .append(count(count, "Zahn", "Zähne")).append(". ");
        if (mitAusnahme.isEmpty()) {
            text.append("Keiner der verbliebenen Tarife leistet für ihren Ersatz; der Ausschluss "
                    + "fehlender Zähne gilt dort ohne Exception.");
            return text.toString();
        }
        if (!ohneAusnahme.isEmpty()) {
            text.append("In ").append(join(ohneAusnahme))
                    .append(" ist der Ersatz fehlender Zähne ausgeschlossen. ");
        }
        text.append(join(mitAusnahme)).append(exceptionVerb(mitAusnahme))
                .append(" eine Exception vor: ").append(withoutPeriod(ausnahmetext)).append(".");
        text.append(" Wie weit die Exception trägt, sagt ihr Wortlaut; er ist maßgeblich.");
        return text.toString();
    }

    static String hintMissingZaehneOpen(List<String> mitAusnahme) {
        return ("Wie viele Zähne bei Vertragsschluss fehlen, ist nicht erhoben. Danach ist zu "
                + "fragen: der Ersatz fehlender Zähne ist ausgeschlossen, eine Exception sehen nur "
                + "%s vor.").formatted(join(mitAusnahme));
    }

    static String hintNameCheaper(String anzeigename, List<String> deckung) {
        return ("Der genannte Bedarf wird auch von %s abgedeckt (%s). Nach dem Grundsatz der "
                + "Offenheit ist der günstigere Tarif zu nennen, und zwar mit seinen Grenzen; die "
                + "Entscheidung trifft die Kundin oder der Kunde.")
                .formatted(anzeigename, String.join("; ", deckung));
    }

    static String hintNoTarif() {
        return "Es gibt hier nichts zu empfehlen, und das ist offen zu sagen. Die Ausschlussgründe "
                + "sind zu nennen, statt einen Tarif zu suchen, der nicht passt: ein Tarif, der "
                + "nicht passt, wird storniert, sobald der erste Leistungsfall enttäuscht.";
    }


    static String promise(Leistung leistung) {
        if (!leistung.versichert()) {
            return "nicht versichert";
        }
        List<String> parts = new ArrayList<>();
        parts.add("zu " + leistung.quote() + " Prozent");
        if (leistung.limitProJahr() != null) {
            parts.add("höchstens " + amount(leistung.limitProJahr()) + " je Versicherungsjahr");
        }
        if (leistung.limitGesamt() != null) {
            parts.add("höchstens " + amount(leistung.limitGesamt()) + " insgesamt");
        }
        if (leistung.faelleProJahr() != null) {
            parts.add("höchstens " + count(leistung.faelleProJahr(), "Fall", "Fälle")
                    + " je Versicherungsjahr");
        }
        if (leistung.maxFaelle() != null) {
            String cases = "höchstens " + count(leistung.maxFaelle(), "Fall", "Fälle");
            if (leistung.zeitraumJahre() != null) {
                cases += " in " + count(leistung.zeitraumJahre(), "Jahr", "Jahren");
            }
            parts.add(cases);
        }
        if (leistung.wartezeitMonate() != null) {
            parts.add("nach einer eigenen Wartezeit von "
                    + count(leistung.wartezeitMonate(), "Monat", "Monaten"));
        }
        if (leistung.bedingung() != null) {
            parts.add("bei " + leistung.bedingung());
        }
        return String.join(", ", parts);
    }

    static String coverageRow(String leistungsbereichName, Leistung leistung) {
        return leistungsbereichName + " " + promise(leistung);
    }

    private static String selbstbehalt(Tarif tarif) {
        if (tarif.selbstbehalt() == null || tarif.selbstbehalt().signum() == 0) {
            return "Ein Selbstbehalt fällt nicht an.";
        }
        return "Der Selbstbehalt beträgt " + amount(tarif.selbstbehalt())
                + " je Versicherungsjahr.";
    }

    private static String wartezeit(Tarif tarif, Vorversicherung vorversicherung) {
        if (tarif.wartezeitMonate() == 0) {
            return "Eine Wartezeit gilt nicht.";
        }
        String kern = "Die Wartezeit beträgt "
                + count(tarif.wartezeitMonate(), "Monat", "Monaten") + ".";
        if (!tarif.wartezeitEntfaelltBeiVorversicherung()) {
            return kern;
        }
        if (vorversicherung == Vorversicherung.LUECKENLOS) {
            return kern + " Sie entfällt hier wegen des lückenlosen Vorversicherungsschutzes.";
        }
        return kern + " Sie entfällt bei lückenlosem Vorversicherungsschutz.";
    }

    static String amount(BigDecimal value) {
        return String.format(Locale.GERMANY, "%,.2f Euro", value);
    }

    static String count(int value, String einzahl, String mehrzahl) {
        return value + " " + (value == 1 ? einzahl : mehrzahl);
    }

    static String join(List<String> parts) {
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " und " + parts.getLast();
    }

    private static String exceptionVerb(List<String> tarife) {
        return tarife.size() == 1 ? " sieht" : " sehen";
    }

    private static String withoutPeriod(String text) {
        String cleaned = sentence(text);
        if (cleaned == null) {
            return "";
        }
        return cleaned.endsWith(".") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }
}
