package consulting.atra.rechenkern.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.generated.model.Leistungsbereich;
import consulting.atra.rechenkern.generated.model.Schadenposition;

import java.math.BigDecimal;

public record SchadenpositionInput(

        @JsonProperty()
        @JsonPropertyDescription("""
                Gebührennummer der Position, vierstellig als String mit \
                führenden Nullen ("0010") -- in der Regel eine GOZ-Nummer, \
                bei GOÄ-Leistungen die GOÄ-Ziffer. Weglassen, wenn die \
                Rechnung keine ausweist: Material, Labor und \
                Verlangensleistungen tragen keine. Dieser Dienst prüft die \
                Nummer nicht gegen die Gebührenordnung und leitet aus ihr \
                auch keinen Leistungsbereich ab -- das Feld ist reine \
                Dokumentation der Position, nichts, worauf hier gerechnet \
                wird. Genau deshalb darf es fehlen.""")
        String goz,

        @JsonPropertyDescription("""
                Leistungsbereich der Position: ZE, IMP, INL, ZERH, PAR, PZR, \
                KFO, FUN, NAR oder AKUT. Pflicht, weil davon Quote und \
                Sublimit abhängen -- und dieser Dienst ordnet ihn nicht zu. \
                Er kennt keine Gebührennummern. Die Zuordnung liefert \
                goz_pruefen im Wissensdienst; wer den Bereich nicht kennt, \
                fragt dort nach statt zu raten.""")
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
        if (leistungsbereich == null) {
            throw new DomainException("Zu " + named() + " fehlt der "
                    + "leistungsbereich; dieser Dienst ordnet ihn nicht zu, das leistet "
                    + "goz_pruefen im Wissensdienst");
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
        return new Schadenposition(leistungsbereich, new BigDecimal(betrag), beschreibung).goz(goz);
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
