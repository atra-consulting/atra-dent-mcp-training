package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.domain.KundenService;
import de.atra.kernsystem.generated.model.BeitragsberechnungResult;
import de.atra.kernsystem.generated.model.Kunde;
import de.atra.kernsystem.generated.model.TarifId;
import de.atra.kernsystem.rechenkern.RechenkernClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class BeitragTools {

    private static final String TARIF = """
            Technischer Schlüssel des Tarifs, einer von vier: ATRA_DENT_S \
            (atra.dent.smart), ATRA_DENT_B (atra.dent.balance), ATRA_DENT_X \
            (atra.dent.brillant), ATRA_DENT_X_SB (atra.dent.brillant mit \
            Selbstbehalt).""";

    private static final String START_OWN = """
            Gewünschter Beginn im Format JJJJ-MM-TT, maßgeblich für das \
            Eintrittsalter. Ohne Angabe wird mit dem heutigen Tag gerechnet — \
            das ist die richtige Annahme für einen Wechsel zum \
            nächstmöglichen Zeitpunkt, nicht für einen später geplanten.""";

    private static final String DESCRIPTION_OWN = """
            Rechnet den Monatsbeitrag für den eigenen Vertrag: Das Geburtsdatum \
            kommt aus der Akte der aufrufenden Kundin, angegeben wird nur der \
            Tarif.

            Damit lässt sich ein Wechsel durchrechnen, ohne nach dem \
            Geburtsdatum zu fragen und ohne es aus einer früheren Antwort \
            abzuschreiben. Für den Vergleich mehrerer Tarife wird das Tool \
            je Tarif einmal aufgerufen.

            Liegt das Eintrittsalter außerhalb der für den Tarif zulässigen \
            Grenzen, ist das ein Fehler und keine Auskunft: der Tarif ist dann \
            nicht abschließbar. Zwei der vier Tarife sind nur von 18 bis 65 \
            abschließbar.

            Der Beitrag ist nur die eine Hälfte eines Wechsels. Die andere sind \
            erneute Gesundheitsprüfung, erneut laufende Wartezeit und eine neu \
            beginnende Zahnstaffel — davon rechnet dieses Tool nichts nach. \
            Ein Vergleich, der nur Beiträge nennt, ist keine Beratung.""";

    private final KundenService kunden;
    private final RechenkernClient rechenkern;

    public BeitragTools(KundenService kunden, RechenkernClient rechenkern) {
        this.kunden = Objects.requireNonNull(kunden, "kunden");
        this.rechenkern = Objects.requireNonNull(rechenkern, "rechenkern");
    }

    @McpTool(name = "mein_beitrag_berechnen", description = DESCRIPTION_OWN,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public BeitragsberechnungResult calculateOwnBeitrag(
            McpTransportContext context,
            @McpToolParam(description = TARIF) TarifId tarifId,
            @McpToolParam(required = false, description = START_OWN) LocalDate gewuenschterBeginn) {

        long kundenId = Mandant.kundenId(context);
        if (tarifId == null) {
            throw new DomainException("tarifId ist Pflicht und muss einer der vier "
                    + "Tarifschluessel sein");
        }
        Kunde kunde = kunden.read(kundenId);
        LocalDate start = gewuenschterBeginn == null ? LocalDate.now() : gewuenschterBeginn;
        return new BeitragsberechnungResult()
                .monatsbeitrag(rechenkern.monthlyBeitrag(kunde.getGeburtsdatum(), tarifId, start));
    }
}
