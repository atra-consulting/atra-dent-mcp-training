package consulting.atra.wissen.beratung;

import java.util.List;

public record BeratungsResult(
        BeratungRequest anliegen,
        DecisionPath weg,
        String zusammenfassung,
        List<Empfehlung> empfehlungen,
        List<ExcludedTarif> ausgeschlossene,
        List<Hinweis> hinweise,
        Principle grundsatz,
        List<ComplianceLimit> complianceGrenzen,
        String vertraulichkeit) {

    public BeratungsResult {
        empfehlungen = empfehlungen == null ? List.of() : List.copyOf(empfehlungen);
        ausgeschlossene = ausgeschlossene == null ? List.of() : List.copyOf(ausgeschlossene);
        hinweise = hinweise == null ? List.of() : List.copyOf(hinweise);

        if (anliegen == null) {
            throw new IllegalArgumentException("Ein Ergebnis ohne das Anliegen ist nicht pruefbar");
        }
        if (weg == null) {
            throw new IllegalArgumentException("Ein Ergebnis braucht einen Entscheidungsweg");
        }
        if (zusammenfassung == null || zusammenfassung.isBlank()) {
            throw new IllegalArgumentException("Ein Ergebnis braucht eine Zusammenfassung");
        }
        if (grundsatz == null) {
            throw new IllegalArgumentException("Ein Ergebnis ohne den Grundsatz des Leitfadens "
                    + "liefert nur die Reihenfolge und damit die Haelfte");
        }
        if (complianceGrenzen.isEmpty()) {
            throw new IllegalArgumentException("Ein Ergebnis ohne Compliance-Grenzen; sie gehen mit "
                    + "jeder Empfehlung mit, auch wenn kein Tarif passt");
        }
        if (vertraulichkeit == null || vertraulichkeit.isBlank()) {
            throw new IllegalArgumentException("Ein Ergebnis braucht die Vertraulichkeit des "
                    + "Leitfadens; ohne sie laesst es sich nicht von einer Bedingungsauskunft "
                    + "unterscheiden");
        }
        if (empfehlungen.isEmpty() != (weg == DecisionPath.KEIN_TARIF)) {
            throw new IllegalArgumentException(
                    "Leere Empfehlungsliste und KEIN_TARIF gehoeren zusammen, hier: " + weg);
        }
        if (empfehlungen.isEmpty() && ausgeschlossene.isEmpty()) {
            throw new IllegalArgumentException(
                    "Kein Tarif empfohlen und keiner ausgeschlossen: das waere eine leere Antwort "
                            + "ohne Erklaerung");
        }
        checkRanges(empfehlungen);
    }

    public Empfehlung bestRecommendation() {
        return empfehlungen.isEmpty() ? null : empfehlungen.getFirst();
    }

    public boolean hasRecommendation() {
        return !empfehlungen.isEmpty();
    }

    private static void checkRanges(List<Empfehlung> empfehlungen) {
        for (int position = 0; position < empfehlungen.size(); position++) {
            int erwartet = position + 1;
            if (empfehlungen.get(position).rang() != erwartet) {
                throw new IllegalArgumentException("Die Raenge sind nicht fortlaufend: an Stelle "
                        + erwartet + " steht Rang " + empfehlungen.get(position).rang());
            }
        }
    }
}
