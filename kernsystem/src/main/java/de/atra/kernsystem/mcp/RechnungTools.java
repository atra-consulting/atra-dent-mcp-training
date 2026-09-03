package de.atra.kernsystem.mcp;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.domain.rechnungsextraktion.RechnungsextraktionService;
import de.atra.kernsystem.generated.model.RechnungsextraktionResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class RechnungTools {

    private static final String DESCRIPTION = """
            Extrahiert die strukturierten Daten einer Zahnarztrechnung aus \
            einem PDF/A-Dokument: Rechnungsnummer, Datum, Absender, Patient, \
            Positionen (Ziffer, Leistung, Zahn, Anzahl, Steigerungssatz, \
            Betrag) sowie Gesamt- und Zahlbetrag.

            Die Extraktion ist deterministisch und layoutunabhängig — reine \
            Textextraktion plus ein fester, generischer Regelsatz, kein \
            Modell. Dieselbe Datei ergibt immer dasselbe Ergebnis; das \
            Tool eignet sich deshalb auch als verlässliche Referenz, um \
            eine eigene Lesart der Rechnung gegenzuprüfen.

            Es wird nur zurückgegeben, was auf der Rechnung steht: Weist der \
            Absender den Steigerungssatz nicht aus, ist faktor null — der \
            Wert darf dann nicht geraten werden. Die Zuordnung der Ziffern \
            zu Leistungsbereichen ist Produktwissen und nicht Teil der \
            Antwort. Kopffelder wie Patient oder Absender können null sein, \
            wenn das Layout sie nicht erkennbar macht.

            Ist das Dokument kein PDF/A oder enthält es keine erkennbaren \
            Rechnungspositionen, schlägt der Aufruf mit einer generischen \
            Fehlermeldung fehl. Es wird nichts gespeichert; insbesondere \
            wird kein Schadensfall angelegt — dafür ist \
            schadensfall_einreichen zuständig.""";

    private static final String PDF_PARAM = """
            Das Rechnungsdokument als Base64-kodiertes PDF/A. Erwartet die \
            rohen Dateibytes in Base64, ohne data:-Präfix und ohne \
            Zeilenumbrüche.""";

    private final RechnungsextraktionService service;

    public RechnungTools(RechnungsextraktionService service) {
        this.service = Objects.requireNonNull(service, "dienst");
    }

    @McpTool(name = "rechnung_extrahieren", description = DESCRIPTION,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public RechnungsextraktionResult extractRechnung(
            @McpToolParam(description = PDF_PARAM) String pdfBase64) {

        if (pdfBase64 == null || pdfBase64.isBlank()) {
            throw new DomainException("pdfBase64 ist Pflicht: das Rechnungsdokument "
                    + "als Base64-kodiertes PDF/A");
        }
        byte[] pdf;
        try {
            pdf = Base64.getDecoder().decode(pdfBase64.strip());
        } catch (IllegalArgumentException cause) {
            throw new DomainException(RechnungsextraktionService.GENERIC_MESSAGE);
        }
        return service.extract(pdf);
    }
}
