package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.domain.KundenService;
import de.atra.kernsystem.generated.model.Kunde;
import de.atra.kernsystem.generated.model.KundeAendern;
import de.atra.kernsystem.generated.model.TarifId;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class VertragTools {

    static final String TARIF = """
            Technischer Schlüssel des neuen Tarifs, einer von vier: \
            ATRA_DENT_S (atra.dent.smart), ATRA_DENT_B (atra.dent.balance), \
            ATRA_DENT_X (atra.dent.brillant), ATRA_DENT_X_SB (atra.dent.brillant \
            mit Selbstbehalt).""";

    private static final String DESCRIPTION_READ = """
            Liest die Akte der aufrufenden Kundin oder des aufrufenden Kunden: \
            Stammdaten, Anschrift und den laufenden Vertrag mit Tarifschlüssel, \
            Versicherungsbeginn, Angabe zur Vorversicherung, Anzahl der bei \
            Antragstellung fehlenden Zähne und Status (aktiv oder inaktiv).

            Das ist der erste Aufruf für so gut wie jede Frage, die mit "mein" \
            oder "ich" anfängt — welcher Tarif gilt, seit wann, was zahle ich. \
            Ohne ihn ist der Tarif des Kunden unbekannt, und ohne Tarif ist \
            jede Auskunft aus den Bedingungswerken beliebig, weil sich die vier \
            Tarife in Quoten, Wartezeiten und Ausschlüssen unterscheiden.

            Das Tool nennt den Monatsbeitrag NICHT: er hängt am \
            Eintrittsalter und wird gerechnet, nicht gespeichert — dafür gibt \
            es mein_beitrag_berechnen. Was der Tarif leistet, steht ebenfalls \
            nicht hier, sondern in den Bedingungswerken.""";

    private static final String DESCRIPTION_TARIFWECHSEL = """
            Trägt einen anderen Tarif in die eigene Akte ein. Der Wechsel wird \
            damit vollzogen, nicht beantragt und nicht geprüft.

            VORHER ANSPRECHEN, IMMER: Ein Tarifwechsel nach oben ist \
            versicherungsrechtlich eine Nachversicherung. Daran hängen erneute \
            Gesundheitsprüfung, erneut laufende Wartezeiten und eine neu \
            beginnende Zahnstaffel — der bisherige Verbrauch zählt für die \
            Staffel des neuen Tarifs nicht mit. Wer nur die Beiträge \
            vergleicht, übersieht genau das. Dieses Tool rechnet davon \
            nichts nach und weist auf nichts hin; es schreibt.

            Was ein Tarif kostet, sagt mein_beitrag_berechnen; was er leistet \
            und was beim Wechsel gilt, steht in den Bedingungswerken und ist \
            dort zu belegen, bevor gewechselt wird.""";

    private static final String START = """
            Neuer Versicherungsbeginn im Format JJJJ-MM-TT. Ohne Angabe bleibt \
            der bisherige stehen — was in aller Regel falsch ist, wenn der \
            Tarif wechselt: das Versicherungsjahr und damit die Staffel laufen \
            ab diesem Datum.""";

    private final KundenService service;

    public VertragTools(KundenService service) {
        this.service = Objects.requireNonNull(service, "dienst");
    }

    @McpTool(name = "mein_vertrag_lesen", description = DESCRIPTION_READ,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public Kunde readOwnVertrag(McpTransportContext context) {
        return service.read(Mandant.kundenId(context));
    }

    @McpTool(name = "meinen_tarif_wechseln", description = DESCRIPTION_TARIFWECHSEL,
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = true, openWorldHint = false))
    public Kunde switchOwnTarif(
            McpTransportContext context,
            @McpToolParam(description = TARIF) TarifId tarifId,
            @McpToolParam(required = false, description = START) LocalDate versicherungsbeginn) {

        long kundenId = Mandant.kundenId(context);
        if (tarifId == null) {
            throw new DomainException("tarifId ist Pflicht und muss einer der vier "
                    + "Tarifschluessel sein");
        }
        return service.update(kundenId,
                new KundeAendern().tarifId(tarifId).versicherungsbeginn(versicherungsbeginn));
    }
}
