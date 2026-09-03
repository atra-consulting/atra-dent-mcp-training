package consulting.atra.agenten.schadensfall.check;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class Bewertungssignal {

    static final String NOTED =
            "Bewertung vermerkt. Der Zug endet damit; rufe kein weiteres Tool und schreibe "
                    + "keine weitere Antwort.";

    private static final Logger log = LoggerFactory.getLogger(Bewertungssignal.class);

    private Bewertungsvorschlag proposal;

    public record Positionsbefund(
            @ToolParam(description = """
                    Die Stelle dieser Position in positionen des Falls -- der Wert, \
                    der im Fallblock bei index steht, beginnend bei null. Immer \
                    angeben; er ist der Schluessel des Befunds. Die Gebuehrennummer \
                    taugt dafuer nicht: Dieselbe Nummer kann mehrfach auf einer \
                    Rechnung stehen, und Material-, Labor- und Verlangenspositionen \
                    tragen ueberhaupt keine.""")
            Integer index,

            @ToolParam(required = false, description = """
                    Die vierstellige Gebuehrennummer aus der Rechnung. Weglassen, \
                    wo die Position keine traegt -- Material, Labor und \
                    Verlangensleistungen. Dann sagt allein der index, welche \
                    Position gemeint ist.""")
            String goz,

            @ToolParam(required = false, description = """
                    Der Leistungsbereich laut goz_pruefen (ZE, IMP, INL, ZERH, PAR, PZR, \
                    KFO, FUN, NAR, AKUT). Weglassen, wenn goz_pruefen keinen nennt.""")
            String leistungsbereich,

            @ToolParam(description = """
                    Der Zustand laut goz_pruefen: ENTHALTEN, NICHT_ENTHALTEN, \
                    NICHT_BESTIMMBAR oder UNBEKANNT. Nicht selbst entschieden.""")
            String zustand,

            @ToolParam(description = """
                    Ein Satz zu dieser Position, moeglichst mit Fundstelle aus \
                    bedingungen_suchen.""")
            String begruendung) {
    }

    @Tool(name = ToolSelection.SIGNAL, description = """
        Gib deine Bewertung dieses Falls ab. Rufe dies GENAU EINMAL und als
        letzten Schritt, wenn du alle Pruefschritte hinter dir hast. Der Zug
        endet damit.

        Du schlaegst vor, du entscheidest nicht: Nach dir prueft eine
        deterministische Regelpruefung deinen Vorschlag gegen die
        Rohergebnisse der Tools und kann aus deiner Freigabe eine Eskalation
        machen. Deine Begruendung bleibt dabei erhalten.""")
    public String handOverBewertung(
            @ToolParam(description = """
                    freigabe oder eskalation. Im Zweifel eskalation: Ein Fall, der \
                    zurueckgeht, kostet eine Sichtung; eine falsche Freigabe kostet \
                    Geld.""")
            String empfehlung,

            @ToolParam(required = false, description = """
                    Der Erstattungsbetrag in EUR als Dezimalstring ("336.00") -- GENAU \
                    das Ergebnis von erstattung_berechnen aus diesem Zug. Nie selbst \
                    gerechnet, nie gerundet, nie geschaetzt. Bei einer Eskalation ohne \
                    Rechnung weglassen.""")
            String erstattungsvorschlag,

            @ToolParam(description = "Je Rechnungsposition ein Befund.")
            List<Positionsbefund> positionen,

            @ToolParam(description = """
                    Deine Begruendung fuer die Sachbearbeitung, in Prosa und in ganzen \
                    Saetzen. Das ist die einzige Stelle dieses Zuges, an der Fliesstext \
                    hingehoert.""")
            String begruendung) {

        return set(new Bewertungsvorschlag(
                recommendation(empfehlung),
                amount(erstattungsvorschlag),
                positionen == null ? List.of() : positionen.stream()
                        .map(befund -> new Bewertungsvorschlag.Position(befund.index(),
                                befund.goz(), befund.leistungsbereich(), befund.zustand(),
                                befund.begruendung()))
                        .toList(),
                begruendung));
    }

    public synchronized Optional<Bewertungsvorschlag> handedOver() {
        return Optional.ofNullable(proposal);
    }

    public List<ToolCallback> callbacks() {
        return List.of(ToolCallbacks.from(this));
    }

    private synchronized String set(Bewertungsvorschlag neuer) {
        if (proposal == null) {
            proposal = neuer;
        } else {
            log.warn("bewertung_abgeben wurde ein zweites Mal gerufen; der erste Aufruf gilt");
        }
        return NOTED;
    }

    private static String recommendation(String genannt) {
        String normalized = genannt == null ? "" : genannt.strip().toLowerCase(Locale.ROOT);
        if (!Bewertungsvorschlag.FREIGABE.equals(normalized)
                && !Bewertungsvorschlag.ESKALATION.equals(normalized)) {
            throw new IllegalArgumentException("empfehlung muss "
                    + Bewertungsvorschlag.FREIGABE + " oder " + Bewertungsvorschlag.ESKALATION
                    + " sein. Nicht verwertbar: " + genannt);
        }
        return normalized;
    }

    private static BigDecimal amount(String genannt) {
        if (genannt == null || genannt.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(genannt.strip());
        } catch (NumberFormatException keinBetrag) {
            log.warn("bewertung_abgeben nannte keinen lesbaren Betrag: {}", genannt);
            return null;
        }
    }
}
