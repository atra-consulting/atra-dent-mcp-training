package consulting.atra.wissen.api;

import consulting.atra.wissen.api.generated.DokumenteApi;
import consulting.atra.wissen.api.generated.model.Dokument;
import consulting.atra.wissen.api.generated.model.Tarifschluessel;
import consulting.atra.wissen.api.generated.model.Vertraulichkeit;
import consulting.atra.wissen.documents.DocumentCatalog;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

@RestController
class DokumenteController implements DokumenteApi {

    private final DocumentCatalog catalog;

    DokumenteController(DocumentCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public ResponseEntity<List<Dokument>> listDocuments(
            Tarifschluessel tarif, Vertraulichkeit vertraulichkeit) {
        List<consulting.atra.wissen.documents.CatalogDocument> found;
        if (tarif == null) {
            found = vertraulichkeit == null
                    ? catalog.documents()
                    : catalog.documents(Mapping.toInternal(vertraulichkeit));
        } else {
            found = vertraulichkeit == null
                    ? catalog.fuerTarif(tarif.getValue())
                    : catalog.fuerTarif(tarif.getValue(), Mapping.toInternal(vertraulichkeit));
        }
        return ResponseEntity.ok(found.stream().map(Mapping::toApi).toList());
    }

    @Override
    public ResponseEntity<Dokument> readDocument(String dokumentId) {
        return ResponseEntity.ok(Mapping.toApi(document(dokumentId)));
    }

    @Override
    public ResponseEntity<Resource> readDocumentPdf(String dokumentId) throws IOException {
        consulting.atra.wissen.documents.CatalogDocument document = document(dokumentId);
        FileSystemResource file = new FileSystemResource(document.pdfDatei());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.dokumentId() + ".pdf")
                        .build()
                        .toString())
                .body(file);
    }

    @Override
    public ResponseEntity<String> readDocumentHtml(String dokumentId) throws IOException {
        consulting.atra.wissen.documents.CatalogDocument document = document(dokumentId);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(Files.readString(document.htmlDatei(), StandardCharsets.UTF_8));
    }

    private consulting.atra.wissen.documents.CatalogDocument document(String dokumentId) {
        return catalog.document(dokumentId).orElseThrow(() -> new DocumentNotFoundException(
                dokumentId,
                catalog.documents().stream()
                        .map(consulting.atra.wissen.documents.CatalogDocument::dokumentId)
                        .toList()));
    }
}
