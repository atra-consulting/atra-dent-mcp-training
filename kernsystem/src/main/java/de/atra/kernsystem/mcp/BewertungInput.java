package de.atra.kernsystem.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.Bewertung;
import de.atra.kernsystem.generated.model.Bewertungsempfehlung;
import de.atra.kernsystem.generated.model.Bewertungsposition;
import de.atra.kernsystem.generated.model.Eskalationsgrund;

import java.math.BigDecimal;
import java.util.List;

public record BewertungInput(

        @JsonPropertyDescription("freigabe oder eskalation. Bei eskalation ist mindestens ein Eskalationsgrund Pflicht.")
        String empfehlung,

        @JsonProperty(required = false)
        @JsonPropertyDescription("""
                Ergebnis von erstattung_berechnen in EUR als Dezimalstring ("336.00"). \
                Pflicht bei freigabe. Nie selbst gerechnet oder geschaetzt.""")
        String erstattungsvorschlag,

        @JsonPropertyDescription("""
                Gruende der Eskalation, je mit code (GOZ_UNCLEAR, BETRAG_DEVIATES, \
                ARZT_FLAGGED, ARZT_UNAVAILABLE, BETRAG_ABOVE_THRESHOLD, \
                VERTRAG_INACTIVE, WARTEZEIT, PATIENT_UNCLEAR, DUPLICATE, \
                EXTRACTION_INCOMPLETE, MODELL_WITHOUT_RESULT, MODELL_ESCALATION) \
                und text.""")
        List<Eskalationsgrund> eskalationsgruende,

        @JsonPropertyDescription("""
                Je Rechnungsposition ein Befund: index, goz, leistungsbereich \
                (aus goz_pruefen, darf bei NICHT_BESTIMMBAR/UNBEKANNT fehlen), \
                zustand (ENTHALTEN, NICHT_ENTHALTEN, NICHT_BESTIMMBAR, \
                UNBEKANNT) und begruendung mit Fundstelle.

                index ist die Stelle der Position in schadensfall.positionen, \
                bei null beginnend, und der Schluessel des Befunds. Immer \
                angeben. Die Gebuehrennummer taugt dafuer nicht: dieselbe \
                Nummer kann mehrfach auf einer Rechnung stehen, und Material-, \
                Labor- und Verlangenspositionen tragen ueberhaupt keine. Deren \
                goz bleibt leer -- dann sagt allein der index, welche Position \
                gemeint ist, und ein Befund ohne beides wird abgelehnt.""")
        List<Bewertungsposition> positionen,

        @JsonProperty(required = false)
        @JsonPropertyDescription("""
                Fachauskunft des Arztservice, unveraendert: plausibilitaet, \
                notwendigkeit, text, hinweis. Weglassen, wenn keine vorliegt.""")
        ArztauskunftInput arztauskunft,

        @JsonPropertyDescription("Begruendung fuer die Sachbearbeitung in Prosa.")
        String begruendung,

        @JsonPropertyDescription("Name des bewertenden Agenten, z. B. schadensfall.")
        String agent,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Modellname, z. B. gemini-3.6-flash.")
        String modell) {

    private static final String PATTERN = "[0-9]{1,10}(\\.[0-9]{1,2})?";

    Bewertung toInternal() {
        if (empfehlung == null) {
            throw new DomainException("bewertung.empfehlung ist Pflicht: freigabe oder eskalation");
        }
        Bewertungsempfehlung value;
        try {
            value = Bewertungsempfehlung.fromValue(empfehlung);
        } catch (IllegalArgumentException cause) {
            throw new DomainException("bewertung.empfehlung muss freigabe oder eskalation sein. "
                    + "Nicht verwertbar: " + empfehlung);
        }
        if (erstattungsvorschlag != null && !erstattungsvorschlag.matches(PATTERN)) {
            throw new DomainException("bewertung.erstattungsvorschlag muss ein Dezimalstring mit "
                    + "hoechstens zwei Nachkommastellen sein. Nicht verwertbar: " + erstattungsvorschlag);
        }
        if (begruendung == null || begruendung.isBlank()) {
            throw new DomainException("bewertung.begruendung fehlt");
        }
        checkEskalationsgruende();
        checkPositionen();
        return new Bewertung()
                .empfehlung(value)
                .erstattungsvorschlag(erstattungsvorschlag == null ? null : new BigDecimal(erstattungsvorschlag))
                .eskalationsgruende(eskalationsgruende == null ? List.of() : eskalationsgruende)
                .positionen(positionen == null ? List.of() : positionen)
                .arztauskunft(arztauskunft == null ? null : arztauskunft.toInternal())
                .begruendung(begruendung)
                .agent(agent == null || agent.isBlank() ? "unbekannt" : agent)
                .modell(modell);
    }

    private void checkEskalationsgruende() {
        if (eskalationsgruende == null) {
            return;
        }
        for (int i = 0; i < eskalationsgruende.size(); i++) {
            Eskalationsgrund grund = eskalationsgruende.get(i);
            if (grund.getCode() == null) {
                throw new DomainException("bewertung.eskalationsgruende[" + i + "].code fehlt");
            }
            if (grund.getText() == null || grund.getText().isBlank()) {
                throw new DomainException("bewertung.eskalationsgruende[" + i + "].text fehlt");
            }
        }
    }

    private void checkPositionen() {
        if (positionen == null) {
            return;
        }
        for (int i = 0; i < positionen.size(); i++) {
            Bewertungsposition position = positionen.get(i);
            if (position.getGoz() != null && !position.getGoz().matches("[0-9]{4}")) {
                throw new DomainException("bewertung.positionen[" + i + "].goz ist nicht "
                        + "vierstellig. Nicht verwertbar: " + position.getGoz());
            }
            if (position.getGoz() == null && position.getIndex() == null) {
                throw new DomainException("bewertung.positionen[" + i + "] hat weder goz noch "
                        + "index -- damit ist nicht zu erkennen, welche Position des "
                        + "Schadensfalls gemeint ist");
            }
            if (position.getZustand() == null) {
                throw new DomainException("bewertung.positionen[" + i + "].zustand fehlt");
            }
            if (position.getBegruendung() == null || position.getBegruendung().isBlank()) {
                throw new DomainException("bewertung.positionen[" + i + "].begruendung fehlt");
            }
        }
    }
}
