package de.atra.kernsystem.domain.rechnungsextraktion;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.RechnungsextraktionResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RechnungsextraktionService {

    public static final String GENERIC_MESSAGE =
            "Das Dokument konnte nicht verarbeitet werden";

    private static final Logger log = LoggerFactory.getLogger(RechnungsextraktionService.class);

    private static final String PDFA_NAMESPACE = "http://www.aiim.org/pdfa/ns/id/";

    private final GenericRechnungParser parser = new GenericRechnungParser();

    public RechnungsextraktionResult extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            checkPdfAMarker(document);
            return parser.parse(new IndentedTextLayer().getText(document));
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception cause) {
            log.debug("Rechnungsextraktion fehlgeschlagen", cause);
            throw new DomainException(GENERIC_MESSAGE);
        }
    }

    private static void checkPdfAMarker(PDDocument document) throws Exception {
        PDMetadata metadata = document.getDocumentCatalog().getMetadata();
        String xmp = metadata == null
                ? ""
                : new String(metadata.toByteArray(), StandardCharsets.UTF_8);
        if (!xmp.contains(PDFA_NAMESPACE)) {
            log.debug("Dokument traegt keine PDF/A-Kennzeichnung");
            throw new DomainException(GENERIC_MESSAGE);
        }
    }
}
