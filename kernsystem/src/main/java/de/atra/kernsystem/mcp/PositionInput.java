package de.atra.kernsystem.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.Leistungsbereich;
import de.atra.kernsystem.generated.model.Schadenposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record PositionInput(

        @JsonProperty()
        @JsonPropertyDescription("""
                Vierstellige Gebührenziffer, als String mit führenden Nullen \
                ("0010") -- in der Regel eine GOZ-Nummer, bei GOÄ-Leistungen \
                die GOÄ-Ziffer. Wird übernommen, wie sie auf der Rechnung \
                steht. Weglassen, wenn die Rechnung keine ausweist: Material, \
                Labor und Verlangensleistungen tragen keine Ziffer. Das ist \
                kein Grund, die Position wegzulassen -- sie gehört mit in den \
                Fall.""")
        String goz,

        @JsonProperty()
        @JsonPropertyDescription("""
                Zahn im FDI-Schema, wie ihn die Rechnung zu dieser Position \
                ausweist ("36"). Weglassen, wo keiner dasteht -- \
                Untersuchung, Mundhygienestatus, professionelle \
                Zahnreinigung und Fluoridierung sind Ganzkieferleistungen \
                und tragen keinen. Nicht herleiten.""")
        String zahn,

        @JsonProperty()
        @JsonPropertyDescription("""
                Behandlungsdatum dieser Position im Format JJJJ-MM-TT, \
                sofern die Rechnung es je Position führt. Eine Rechnung kann \
                mehrere Behandlungstage umfassen; deshalb steht das Datum an \
                der Position und nicht nur am Fall.""")
        String datum,

        @JsonProperty()
        @JsonPropertyDescription("""
                Anzahl bzw. Menge, sofern die Rechnung sie ausweist. Der \
                betrag ist immer der Betrag der ganzen Position, nicht der \
                einer Einheit.""")
        Integer anzahl,

        @JsonProperty()
        @JsonPropertyDescription("""
                Leistungsbereich der Position: ZE, IMP, INL, ZERH, PAR, PZR, \
                KFO, FUN, NAR oder AKUT. Darf fehlen, wenn die Rechnung ihn nicht \
                hergibt -- die Zuordnung ist Produktwissen und wird bei der \
                Pruefung nachgetragen. Wird er angegeben, dann nicht geraten: \
                der Nummernkreis der GOZ legt ihn nicht fest.""")
        Leistungsbereich leistungsbereich,

        @JsonPropertyDescription("""
                Betrag dieser Position in EUR als Dezimalstring mit zwei \
                Nachkommastellen ("780.00"), nie als Zahl.""")
        String betrag,

        @JsonPropertyDescription("Leistungstext der Position, wie er auf der Rechnung steht.")
        String beschreibung) {

    private static final String PATTERN = "[0-9]{1,10}(\\.[0-9]{1,2})?";

    Schadenposition toInternal() {
        if (goz != null && !goz.matches("[0-9]{4}")) {
            throw new DomainException("Gebuehrennummern sind vierstellig und werden als "
                    + "String angegeben, mit fuehrenden Nullen. Wo die Rechnung keine ausweist "
                    + "-- Material, Labor, Verlangensleistungen --, bleibt das Feld weg. Nicht "
                    + "verwertbar: " + goz);
        }
        if (betrag == null || !betrag.matches(PATTERN)) {
            throw new DomainException("Der Betrag zu " + named() + " muss ein "
                    + "Dezimalstring mit hoechstens zwei Nachkommastellen sein, etwa "
                    + "\"780.00\". Nicht verwertbar: " + betrag);
        }
        if (beschreibung == null || beschreibung.isBlank()) {
            throw new DomainException("Zu " + named() + " fehlt die "
                    + "beschreibung, also der Leistungstext der Rechnungsposition");
        }
        if (anzahl != null && anzahl < 1) {
            throw new DomainException("Die anzahl zu " + named() + " ist die Menge der "
                    + "abgerechneten Leistung und damit mindestens 1. Wo die Rechnung keine "
                    + "ausweist, bleibt das Feld weg. Nicht verwertbar: " + anzahl);
        }
        return new Schadenposition(new BigDecimal(betrag), beschreibung)
                .goz(goz)
                .zahn(zahn)
                .datum(datum == null || datum.isBlank() ? null : dateRead())
                .anzahl(anzahl)
                .leistungsbereich(leistungsbereich);
    }

    private LocalDate dateRead() {
        try {
            return LocalDate.parse(datum.strip());
        } catch (DateTimeParseException cause) {
            throw new DomainException("Das datum zu " + named() + " muss im Format "
                    + "JJJJ-MM-TT angegeben werden. Nicht verwertbar: " + datum);
        }
    }

    private String named() {
        if (goz != null) {
            return "Gebuehrennummer " + goz;
        }
        if (beschreibung != null && !beschreibung.isBlank()) {
            return "der Position \"" + beschreibung.strip() + "\"";
        }
        if (betrag != null && !betrag.isBlank()) {
            return "der Position ueber " + betrag + " EUR";
        }
        return "einer Position ohne jede Angabe";
    }
}
