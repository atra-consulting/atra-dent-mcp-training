package consulting.atra.rechenkern.mcp;

import consulting.atra.rechenkern.domain.Erstattungsberechnung;
import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungRequest;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungResult;
import consulting.atra.rechenkern.generated.model.Schadenposition;
import consulting.atra.rechenkern.generated.model.TarifId;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class ErstattungTools {

    private static final String DESCRIPTION_ERSTATTUNG = """
            Berechnet die Erstattung zu einer Zahnarztrechnung: aus Tarif, \
            Rechnungspositionen und bisherigem Verbrauch ergibt sich der \
            auszuzahlende Betrag, ausgewiesen mit der Schrittfolge, die ihn \
            belegt (erstattungsfähiger Betrag, Quote, Sublimit, \
            GKV-Vorleistung, Selbstbehalt, Zahnstaffel/Jahreshöchstgrenze). \
            Die Rechnung ist zustandslos: es wird nichts gespeichert, kein \
            Schadensfall angelegt und keiner geändert. Wer die Erstattung \
            tatsächlich geltend machen will, braucht danach \
            schadensfall_einreichen im Kernsystem -- dieses Tool liefert nur \
            die Auskunft dazu, im Voraus oder zur Kontrolle.

            Weil nichts gespeichert wird, kennt dieses Tool auch keinen \
            bisherigen Verbrauch von sich aus: der Aufrufer liefert ihn mit, \
            im Parameter verbrauch. Die Quelle dafür ist \
            GET /kunden/{id}/schadensfaelle im Kernsystem -- daraus ergibt \
            sich, was im laufenden Versicherungsjahr und seit \
            Versicherungsbeginn schon ausgezahlt wurde. Ohne verbrauch wird \
            mit 0.00 gerechnet, was dem ersten Schadensfall eines Vertrags \
            entspricht; bei einem bestehenden Vertrag mit Vorgeschichte \
            fällt die Erstattung dann zu hoch aus.

            Jede Position in positionen muss einen leistungsbereich tragen \
            (ZE, IMP, INL, ZERH, PAR, PZR, KFO, FUN, NAR, AKUT) -- ohne ihn \
            lassen sich weder Quote noch Sublimit bestimmen. DIESER DIENST \
            ORDNET IHN NICHT ZU und kennt keine Gebührennummern. Wer nur die \
            GOZ-Nummern der Rechnung hat, ermittelt den Leistungsbereich \
            vorher mit goz_pruefen im Wissensdienst und übergibt hier erst \
            das Ergebnis.

            Was das Tool NICHT leistet: keine Wartezeitprüfung -- ob eine \
            Wartezeit für den Tarif schon abgelaufen ist, geht in diese \
            Rechnung nicht ein. Keine Prüfung individueller \
            Leistungsausschlüsse. Und keine Zusage über eine Auszahlung: das \
            Ergebnis ist eine Auskunft, keine Entscheidung -- über \
            Genehmigung und Auszahlung eines Schadensfalls entscheidet die \
            Sachbearbeitung im Kernsystem, nicht dieses Tool.""";

    private static final String TARIF = """
            Technischer Schlüssel des Tarifs, einer von vier: ATRA_DENT_S \
            (atra.dent.smart), ATRA_DENT_B (atra.dent.balance), ATRA_DENT_X \
            (atra.dent.brillant), ATRA_DENT_X_SB (atra.dent.brillant mit \
            Selbstbehalt). Bestimmt Quoten, Sublimits, Selbstbehalt und \
            Zahnstaffel -- zu finden mit tarife_auflisten oder tarif_lesen.""";

    private static final String VERSICHERUNGSBEGINN = """
            Vertragsbeginn im Format JJJJ-MM-TT, Bezugspunkt für \
            Versicherungsjahr und Zahnstaffel. Nicht das Datum, an dem der \
            Antrag gestellt wurde, sondern der tatsächliche Beginn des \
            Versicherungsschutzes.""";

    private static final String BEHANDLUNGSDATUM = """
            Datum der Behandlung im Format JJJJ-MM-TT, wie es auf der \
            Rechnung steht -- nicht das Rechnungsdatum und nicht das \
            heutige Datum. Zusammen mit versicherungsbeginn ergibt sich \
            daraus das Versicherungsjahr, gezählt in 12-Monats-Zeiträumen \
            ab Vertragsbeginn, nicht in Kalenderjahren.""";

    private static final String POSITIONEN = """
            Die Positionen der Rechnung, mindestens eine. Jede Position \
            trägt leistungsbereich, betrag und beschreibung, dazu goz, \
            sofern die Rechnung eine Ziffer ausweist -- siehe die \
            Beschreibung der einzelnen Felder. Der Rechnungsbetrag im \
            Ergebnis ist die Summe dieser Positionen und wird nicht separat \
            übergeben; wer Material- und Laborpositionen weglässt, bekommt \
            deshalb eine zu niedrige Erstattung.""";

    private static final String GKV_LEISTUNG = """
            Vorleistung der gesetzlichen Krankenversicherung in EUR, als \
            Dezimalstring ("300.00"). Die Quoten des Tarifs verstehen sich \
            einschließlich dieser Vorleistung: erst wird die Quote auf den \
            vollen Rechnungsbetrag angewendet, danach die GKV-Leistung \
            abgezogen. Ohne Angabe wird mit 0.00 gerechnet.""";

    private static final String UNFALLBEDINGT = """
            Behandlung infolge eines Unfalls nach Versicherungsbeginn. Dann \
            entfällt die Zahnstaffel; die Jahreshöchstgrenze bleibt und \
            greift sofort. Ohne Angabe: false.""";

    private static final String VORVERBRAUCH = """
            Bisheriger Verbrauch des Vertrags, mit dem gerechnet wird -- \
            dieser Dienst führt keine Akte und weiß von sich aus nichts \
            davon. Quelle ist GET /kunden/{id}/schadensfaelle im \
            Kernsystem; angerechnet wird stets das tatsächlich Ausgezahlte, \
            nicht das rechnerisch Ermittelte. Ohne Angabe wird überall mit \
            0.00 gerechnet, was dem ersten Schadensfall eines Vertrags \
            entspricht.

            Trägt zwei getrennte Bereichsfelder, und sie sind NICHT \
            austauschbar: leistungsbereiche zählt nur das laufende \
            Versicherungsjahr (Bezugsgröße für ein Sublimit mit \
            limitProJahr) und setzt mit jedem neuen Versicherungsjahr wieder \
            bei 0.00 an; leistungsbereicheSeitVersicherungsbeginn zählt \
            kumuliert über die gesamte Vertragslaufzeit (Bezugsgröße für ein \
            Sublimit mit limitGesamt, etwa KFO) und wird nie zurückgesetzt. \
            Wer die beiden vertauscht, bekommt ein falsches Sublimit.""";

    private final Erstattungsberechnung calculation;

    public ErstattungTools(Erstattungsberechnung berechnung) {
        this.calculation = Objects.requireNonNull(berechnung, "berechnung");
    }

    @McpTool(name = "erstattung_berechnen", description = DESCRIPTION_ERSTATTUNG,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public ErstattungsberechnungResult calculateErstattung(
            @McpToolParam(required = true, description = TARIF) TarifId tarifId,
            @McpToolParam(required = true, description = VERSICHERUNGSBEGINN)
                    LocalDate versicherungsbeginn,
            @McpToolParam(required = true, description = BEHANDLUNGSDATUM)
                    LocalDate behandlungsdatum,
            @McpToolParam(required = true, description = POSITIONEN)
                    List<SchadenpositionInput> positionen,
            @McpToolParam(required = false, description = GKV_LEISTUNG) String gkvLeistung,
            @McpToolParam(required = false, description = UNFALLBEDINGT) Boolean unfallbedingt,
            @McpToolParam(required = false, description = VORVERBRAUCH) VorverbrauchInput verbrauch) {

        if (tarifId == null || versicherungsbeginn == null || behandlungsdatum == null) {
            throw new DomainException("tarifId, versicherungsbeginn und behandlungsdatum "
                    + "sind Pflicht");
        }
        if (positionen == null || positionen.isEmpty()) {
            throw new DomainException("positionen darf nicht leer sein: ohne Position gibt "
                    + "es nichts zu berechnen");
        }

        List<Schadenposition> checked = positionen.stream()
                .map(SchadenpositionInput::toInternal).toList();

        ErstattungsberechnungRequest request = new ErstattungsberechnungRequest(
                tarifId, versicherungsbeginn, behandlungsdatum, checked)
                .gkvLeistung(amount(gkvLeistung))
                .unfallbedingt(Boolean.TRUE.equals(unfallbedingt))
                .verbrauch(verbrauch == null ? null : verbrauch.toInternal());

        return calculation.calculate(request);
    }

    private static final String PATTERN = "[0-9]{1,10}(\\.[0-9]{1,2})?";

    private static BigDecimal amount(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches(PATTERN)) {
            throw new DomainException("gkvLeistung muss ein Dezimalstring mit hoechstens "
                    + "zwei Nachkommastellen sein, etwa \"300.00\". Nicht verwertbar: " + value);
        }
        return new BigDecimal(value);
    }
}
