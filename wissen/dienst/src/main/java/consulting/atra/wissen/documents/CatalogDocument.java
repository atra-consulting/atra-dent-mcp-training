package consulting.atra.wissen.documents;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record CatalogDocument(
        String dokumentId,
        String title,
        DocumentType art,
        Confidentiality vertraulichkeit,
        Optional<String> tarif,
        Set<String> gueltigFuer,
        Path pdfDatei,
        Path htmlDatei) {

    public CatalogDocument {
        Objects.requireNonNull(dokumentId, "dokumentId");
        Objects.requireNonNull(title, "titel");
        Objects.requireNonNull(art, "art");
        Objects.requireNonNull(vertraulichkeit, "vertraulichkeit");
        Objects.requireNonNull(tarif, "tarif");
        gueltigFuer = Set.copyOf(gueltigFuer);
        Objects.requireNonNull(pdfDatei, "pdfDatei");
        Objects.requireNonNull(htmlDatei, "htmlDatei");
    }

    public boolean giltFuer(String tarifschluessel) {
        return gueltigFuer.contains(tarifschluessel);
    }

    public String pdfUrl() {
        return "/dokumente/" + dokumentId + "/pdf";
    }

    public String htmlUrl() {
        return "/dokumente/" + dokumentId + "/html";
    }

    public String htmlUrl(String abschnittId) {
        return htmlUrl() + "#" + abschnittId;
    }
}
