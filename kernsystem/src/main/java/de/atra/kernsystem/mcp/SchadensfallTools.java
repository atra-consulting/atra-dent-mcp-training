package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.domain.SchadensfallService;
import de.atra.kernsystem.generated.model.Protokollakteur;
import de.atra.kernsystem.generated.model.Protokolleintrag;
import de.atra.kernsystem.generated.model.Schaden;
import de.atra.kernsystem.generated.model.Schadenposition;
import de.atra.kernsystem.generated.model.Schadensfall;
import de.atra.kernsystem.generated.model.SchadensfallBewerten;
import de.atra.kernsystem.generated.model.Schadensfallstatus;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class SchadensfallTools {

    private static final String DESCRIPTION_LIST = """
            Listet die Schadensfälle der eigenen Akte auf, mit Behandlungsdatum, \
            Rechnungsbetrag, Status, Erstattungsbetrag und den einzelnen \
            Rechnungspositionen.

            Jeder Fall hat einen von sieben Zuständen: eingereicht (liegt vor, \
            noch nicht angesehen), in_pruefung (ein Agent prüft gerade), \
            geprueft_freigabe (der Agent empfiehlt die Freigabe, ein \
            Erstattungsvorschlag liegt in bewertung), geprueft_eskalation (der \
            Agent hat den Fall an die Sachbearbeitung gegeben; die Gründe \
            stehen in bewertung.eskalationsgruende), genehmigt (entschieden, \
            noch nicht ausgezahlt), abgelehnt (mit ablehnungsgrund und \
            gegebenenfalls einem Freitexthinweis), ausgezahlt (erledigt). Ein \
            erstattungsbetrag von null heißt nicht "null Euro", sondern "noch \
            nicht entschieden" — ein erstattungsvorschlag in der Bewertung ist \
            eine Empfehlung, keine Zusage.

            Das ist auch die Quelle für den bisherigen Verbrauch: was in einem \
            Versicherungsjahr schon ausgezahlt wurde, entscheidet über Staffel, \
            Jahreshöchstgrenze und Selbstbehalt. Angerechnet wird dabei stets \
            das tatsächlich Ausgezahlte.

            Eine leere Liste ist ein gültiges Ergebnis und heißt: dieser Kunde \
            hat keine Schadensfälle, die dem Filter entsprechen.""";

    private static final String DESCRIPTION_READ = """
            Liest einen einzelnen Schadensfall der eigenen Akte anhand seiner \
            Nummer.

            Nur die eigene Akte: Eine Schadensfallnummer, die zu einem anderen \
            Kunden gehört, wird genauso beantwortet wie eine, die es nicht \
            gibt. Aus der Antwort lässt sich deshalb nicht schließen, ob die \
            Nummer vergeben ist — Nummern durchzuprobieren führt zu nichts.

            Wenn die Nummer nicht bekannt ist, führt meine_schadensfaelle_auflisten \
            hin; raten führt zu einer Fehlermeldung.""";

    private static final String DESCRIPTION_SUBMIT = """
            Reicht eine Zahnarztrechnung als neuen Schadensfall in der eigenen \
            Akte ein. Der Fall wird angelegt und bekommt den Status \
            eingereicht; der Rechnungsbetrag wird als Summe der Positionen \
            gebildet und nicht übergeben.

            DAS SCHREIBT UND LÄSST SICH NICHT ZURÜCKNEHMEN. Es gibt kein \
            Tool, das einen Schadensfall löscht oder korrigiert. \
            Eingereicht wird deshalb erst, wenn die Positionen vollständig \
            und geprüft sind — und nicht, um etwas auszuprobieren.

            Über die Erstattung sagt das Einreichen nichts. Es entscheidet \
            nichts, rechnet nichts und sagt keine Zahlung zu. Geprüft wird der \
            Fall danach — vom Schadensfallagenten oder der Sachbearbeitung. \
            Der leistungsbereich je Position darf deshalb fehlen: Die Rechnung \
            nennt Gebührennummern, keine Leistungsbereiche; die Zuordnung ist \
            Produktwissen und wird bei der Prüfung nachgetragen. Wer ihn \
            angibt, muss ihn wissen (goz_pruefen), nicht raten.""";

    private static final String STATUS = """
            Filtert auf einen Zustand: eingereicht, in_pruefung, \
            geprueft_freigabe, geprueft_eskalation, genehmigt, abgelehnt oder \
            ausgezahlt. Ohne Angabe kommen alle Fälle.""";

    private static final String NUMBER = """
            Die Schadensfallnummer, wie sie meine_schadensfaelle_auflisten \
            ausweist. Eine ganze Zahl.""";

    private static final String BEHANDLUNGSDATUM = """
            Datum der Behandlung im Format JJJJ-MM-TT, wie es auf der Rechnung \
            steht — nicht das Rechnungsdatum und nicht das heutige Datum. \
            Daran hängen Versicherungsjahr, Wartezeit und Staffel.""";

    private static final String POSITIONEN = """
            Die Positionen der Rechnung, ALLE, mindestens eine. Jede Position \
            hat bis zu sieben Felder; verpflichtend sind nur betrag und \
            beschreibung:

            goz — die vierstellige Gebührennummer als String, mit führenden \
            Nullen ("0010"); in der Regel eine GOZ-, bei GOÄ-Leistungen die \
            GOÄ-Ziffer. Als Zahl gingen die Nullen verloren. WEGLASSEN, wo die \
            Rechnung keine ausweist: Material, Labor (Paragraf 9 GOZ) und \
            Verlangensleistungen tragen keine Ziffer. Das ist kein Grund, die \
            Position wegzulassen — sie gehört mit in den Fall, und bei \
            Zahnersatz ist sie oft der größere Teil der Rechnung. Die Summe \
            aller Positionen ergibt den Gesamtbetrag der Rechnung.

            zahn — der Zahn im FDI-Schema ("36"), wie ihn die Rechnung zu \
            dieser Position ausweist. Weglassen, wo keiner dasteht: \
            Untersuchung, Mundhygienestatus, professionelle Zahnreinigung und \
            Fluoridierung gelten dem ganzen Kiefer. Nicht herleiten.

            datum — das Behandlungsdatum DIESER Position (JJJJ-MM-TT), sofern \
            die Rechnung es je Position führt. Eine Rechnung kann mehrere \
            Behandlungstage umfassen; das Datum am Fall ist das früheste.

            anzahl — Menge bzw. Faktor der Position, sofern die Rechnung sie \
            ausweist. Der betrag ist immer der Betrag der ganzen Position, \
            nicht der einer Einheit.

            leistungsbereich — optional, siehe oben; wenn angegeben, dann der, \
            dem die Position zugeordnet wird, einer von: ZE \
            (Zahnersatz, auch die Krone auf einem Implantat), IMP (Implantat \
            selbst — Wurzel, Aufbau, Knochenaufbau), INL (laborgefertigte \
            Inlays und Onlays), ZERH (Zahnerhalt: Füllungen, Wurzelbehandlung), \
            PAR (Parodontose), PZR (Prophylaxe und Zahnreinigung), KFO \
            (Kieferorthopädie), FUN (Funktionsanalyse, Aufbissschiene), NAR \
            (Narkose und Sedierung, nicht die örtliche Betäubung), AKUT \
            (Schmerzausschaltung). Der Nummernkreis der GOZ legt den Bereich \
            NICHT fest: eine Krone auf einem Implantat zählt zu ZE, obwohl die \
            Nummer aus dem Implantatabschnitt stammt. Im Zweifel klärt \
            goz_pruefen die Zuordnung, bevor eingereicht wird — geraten \
            verschiebt sie die Quote.

            betrag — der Betrag dieser Position in EUR als Dezimalstring mit \
            zwei Nachkommastellen ("780.00"), nie als Zahl.

            beschreibung — der Leistungstext, wie er auf der Rechnung steht.""";

    private static final String RECHNUNG = """
            Kopfdaten der Rechnung, wie sie rechnung_extrahieren geliefert hat: \
            rechnungsnummer, rechnungsdatum (JJJJ-MM-TT), absender, patient, \
            gesamtbetrag (Dezimalstring). Alle Felder optional; nichts erfinden, \
            was die Rechnung nicht hergibt.""";

    private static final String DESCRIPTION_ASSESS = """
            Speichert die Bewertung des Schadensfallagenten zu einem Fall der \
            eigenen Akte und stellt ihn auf geprueft_freigabe (empfehlung \
            freigabe) oder geprueft_eskalation (empfehlung eskalation). Nur aus \
            dem Zustand in_pruefung; sonst antwortet das Tool mit einem \
            Konflikt, und der Fall wurde inzwischen von jemand anderem \
            bearbeitet — dann nicht wiederholen, sondern neu lesen.

            DAS IST EIN VORSCHLAG, KEINE ENTSCHEIDUNG. Der Erstattungsbetrag \
            und die Zustände genehmigt, abgelehnt, ausgezahlt bleiben der \
            Sachbearbeitung vorbehalten; kein Tool setzt sie. Der \
            erstattungsvorschlag ist ausschließlich das Ergebnis von \
            erstattung_berechnen — nie gerechnet, geschätzt oder gerundet.

            Mit der Bewertung werden Leistungsbereiche, die beim Einreichen \
            fehlten, an den Positionen nachgetragen (aus bewertung.positionen, \
            gleiche GOZ-Nummer). Die Protokollschritte landen im \
            Bearbeitungsprotokoll des Falls, das die Sachbearbeitung sieht — \
            kurz, sachlich, ein Eintrag je Prüfschritt.""";

    private static final String PROTOKOLL = """
            Die Prüfschritte in Reihenfolge, je mit zeitpunkt (ISO-8601), \
            akteur (agent), schritt (kurz, z. B. "GOZ-Pruefung"), detail \
            (ein Satz) und optional calls: die Aufrufe dieses Schritts mit art \
            (modell, tool, a2a), name, eingabeKurz, ergebnisKurz, dauerMs.""";

    private final SchadensfallService service;

    public SchadensfallTools(SchadensfallService service) {
        this.service = Objects.requireNonNull(service, "dienst");
    }

    @McpTool(name = "meine_schadensfaelle_auflisten", description = DESCRIPTION_LIST,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public List<Schadensfall> listOwnSchadensfaelle(
            McpTransportContext context,
            @McpToolParam(required = false, description = STATUS) String status) {

        return service.history(Mandant.kundenId(context), statusFilter(status));
    }

    private static List<Schadensfallstatus> statusFilter(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        try {
            return List.of(Schadensfallstatus.fromValue(status));
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unbekannter Status '" + status + "'. Erlaubt: eingereicht, "
                    + "in_pruefung, geprueft_freigabe, geprueft_eskalation, genehmigt, abgelehnt, ausgezahlt");
        }
    }

    @McpTool(name = "schadensfall_lesen", description = DESCRIPTION_READ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public Schadensfall readSchadensfall(
            McpTransportContext context,
            @McpToolParam(required = true, description = NUMBER) Long schadensfallId) {

        long kundenId = Mandant.kundenId(context);
        if (schadensfallId == null) {
            throw new DomainException("schadensfallId ist Pflicht");
        }
        return service.readFor(kundenId, schadensfallId);
    }

    @McpTool(name = "schadensfall_einreichen", description = DESCRIPTION_SUBMIT,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, openWorldHint = false))
    public Schadensfall submitSchadensfall(
            McpTransportContext context,
            @McpToolParam(required = true, description = BEHANDLUNGSDATUM) LocalDate behandlungsdatum,
            @McpToolParam(required = true, description = POSITIONEN) List<PositionInput> positionen,
            @McpToolParam(required = false, description = RECHNUNG) RechnungskopfInput rechnung) {

        long kundenId = Mandant.kundenId(context);
        if (behandlungsdatum == null) {
            throw new DomainException("behandlungsdatum ist Pflicht");
        }
        if (positionen == null || positionen.isEmpty()) {
            throw new DomainException("positionen darf nicht leer sein: ohne Position gibt es "
                    + "nichts einzureichen");
        }
        List<Schadenposition> checked = positionen.stream().map(PositionInput::toInternal).toList();

        Schaden schaden = new Schaden()
                .kundenId(kundenId)
                .behandlungsdatum(behandlungsdatum)
                .positionen(checked)
                .rechnung(rechnung == null ? null : rechnung.toInternal());
        return service.submit(schaden, Protokollakteur.KUNDE);
    }

    @McpTool(name = "schadensfall_bewerten", description = DESCRIPTION_ASSESS,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, openWorldHint = false))
    public Schadensfall assessSchadensfall(
            McpTransportContext context,
            @McpToolParam(required = true, description = NUMBER) Long schadensfallId,
            @McpToolParam(required = true, description = "Die Bewertung, siehe Toolbeschreibung.") BewertungInput bewertung,
            @McpToolParam(required = false, description = PROTOKOLL) List<ProtokolleintragInput> protokoll) {

        long kundenId = Mandant.kundenId(context);
        if (schadensfallId == null) {
            throw new DomainException("schadensfallId ist Pflicht");
        }
        if (bewertung == null) {
            throw new DomainException("bewertung ist Pflicht");
        }
        service.readFor(kundenId, schadensfallId);
        List<Protokolleintrag> checkedProtokoll = protokoll == null ? List.of()
                : protokoll.stream().map(ProtokolleintragInput::toInternal).toList();
        return service.assess(schadensfallId, new SchadensfallBewerten()
                .bewertung(bewertung.toInternal())
                .protokoll(checkedProtokoll));
    }
}
