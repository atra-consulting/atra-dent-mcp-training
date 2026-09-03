package consulting.atra.rechenkern.mcp;

import consulting.atra.rechenkern.domain.BeitragsService;
import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.domain.TarifService;
import consulting.atra.rechenkern.generated.model.BeitragsberechnungResult;
import consulting.atra.rechenkern.generated.model.Tarif;
import consulting.atra.rechenkern.generated.model.TarifId;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class TarifTools {

    private static final String TARIF = """
            Technischer Schlüssel des Tarifs, einer von vier: ATRA_DENT_S \
            (atra.dent.smart), ATRA_DENT_B (atra.dent.balance), ATRA_DENT_X \
            (atra.dent.brillant), ATRA_DENT_X_SB (atra.dent.brillant mit \
            Selbstbehalt).""";

    private static final String DATE_OF_BIRTH = """
            Geburtsdatum im Format JJJJ-MM-TT. Pflichtangabe, weil daraus mit \
            dem gewünschten Beginn das Eintrittsalter folgt und daran der \
            Beitrag hängt. Ist das Geburtsdatum nicht bekannt, muss danach \
            gefragt werden — geraten kommt eine plausible, aber falsche Zahl \
            heraus. Geht es um den eigenen Vertrag der aufrufenden Kundin, ist \
            mein_beitrag_berechnen das richtige Tool: es nimmt das \
            Geburtsdatum aus der Akte. Das Tool liegt im Kernsystem, nicht in \
            diesem Dienst.""";

    private static final String DESIRED_START = """
            Gewünschter Versicherungsbeginn im Format JJJJ-MM-TT. Zusammen mit \
            dem Geburtsdatum ergibt er das Eintrittsalter.""";

    private static final String DESCRIPTION_LIST = """
            Listet die vier wählbaren Tarife von atra.dent auf, je mit \
            technischem Schlüssel, Anzeigename und basisbeitragMonatlich.

            basisbeitragMonatlich ist der Beitrag des jüngsten Eintrittsalters \
            (18-30), also ein Ab-Preis — kein persönlicher Beitrag. Was jemand \
            tatsächlich zahlt, hängt am eigenen Eintrittsalter und wird mit \
            beitrag_berechnen (hier) oder mein_beitrag_berechnen (im \
            Kernsystem) ermittelt; die beiden sind maßgeblich, nicht diese \
            Liste.

            Was die Tarife inhaltlich unterscheidet — Quoten, Wartezeiten, \
            Sublimits, Staffeln, Ausschlüsse — steht nicht hier, sondern in den \
            Bedingungswerken des Wissensdienstes. Dieser Dienst rechnet nur; er \
            führt weder Verträge noch Produktwissen.""";

    private static final String DESCRIPTION_TARIF = """
            Liest einen einzelnen Tarif anhand seines Schlüssels: Anzeigename \
            und basisbeitragMonatlich.

            basisbeitragMonatlich ist der Beitrag des jüngsten Eintrittsalters \
            (18-30), also ein Ab-Preis; der persönliche Beitrag kommt aus \
            beitrag_berechnen (hier) oder mein_beitrag_berechnen (im \
            Kernsystem).

            Auch hier: keine Leistungsdetails. Ob ein Tarif für eine \
            Behandlung leistet, steht in den Bedingungswerken; in welcher \
            Höhe die Erstattung ausfällt, ermittelt erstattung_berechnen \
            (hier).""";

    private static final String DESCRIPTION_BEITRAG = """
            Ermittelt den Monatsbeitrag für einen Tarif aus Geburtsdatum und \
            gewünschtem Versicherungsbeginn. Die Rechnung ist zustandslos: es \
            wird nichts gespeichert, kein Vertrag angelegt und keiner geändert.

            Das ist das Tool für Interessenten und für Dritte — für jemanden, \
            der noch keine Akte hat oder für den gerechnet wird. Für den eigenen \
            Vertrag der aufrufenden Kundin ist mein_beitrag_berechnen richtig, \
            weil es das Geburtsdatum nicht braucht — dieses Tool liegt im \
            Kernsystem, nicht in diesem Dienst; der Rechenkern kennt keine \
            Kunden.

            Liegt das Eintrittsalter außerhalb der für den Tarif zulässigen \
            Grenzen, ist das ein Fehler und keine Auskunft: der Tarif ist dann \
            nicht abschließbar. Zwei der vier Tarife sind nur von 18 bis 65 \
            abschließbar.""";

    private final TarifService tarife;
    private final BeitragsService beitraege;

    public TarifTools(TarifService tarife, BeitragsService beitraege) {
        this.tarife = Objects.requireNonNull(tarife, "tarife");
        this.beitraege = Objects.requireNonNull(beitraege, "beitraege");
    }

    @McpTool(name = "tarife_auflisten", description = DESCRIPTION_LIST,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public List<Tarif> listTarife() {
        return tarife.all();
    }

    @McpTool(name = "tarif_lesen", description = DESCRIPTION_TARIF,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public Tarif readTarif(
            @McpToolParam(required = true, description = TARIF) TarifId tarifId) {

        if (tarifId == null) {
            throw new DomainException("tarifId ist Pflicht und muss einer der vier "
                    + "Tarifschluessel sein");
        }
        return tarife.read(tarifId);
    }

    @McpTool(name = "beitrag_berechnen", description = DESCRIPTION_BEITRAG,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public BeitragsberechnungResult calculateBeitrag(
            @McpToolParam(required = true, description = DATE_OF_BIRTH) LocalDate geburtsdatum,
            @McpToolParam(required = true, description = TARIF) TarifId tarifId,
            @McpToolParam(required = true, description = DESIRED_START) LocalDate gewuenschterBeginn) {

        if (geburtsdatum == null || tarifId == null || gewuenschterBeginn == null) {
            throw new DomainException("geburtsdatum, tarifId und gewuenschterBeginn sind "
                    + "Pflicht; fuer den eigenen Vertrag genuegt mein_beitrag_berechnen "
                    + "im Kernsystem");
        }
        return new BeitragsberechnungResult()
                .monatsbeitrag(beitraege.calculateMonthly(geburtsdatum, tarifId, gewuenschterBeginn));
    }
}
