package consulting.atra.wissen.search;

import consulting.atra.wissen.documents.CatalogDocument;

public record Hit(
        String dokumentId,
        String abschnittId,
        String ueberschrift,
        String text,
        double bewertung,
        String htmlUrl,
        String pdfUrl) {

    static Hit of(Passage passage, CatalogDocument document, double bewertung) {
        return new Hit(
                passage.dokumentId(),
                passage.abschnittId(),
                passage.ueberschrift(),
                passage.text(),
                bewertung,
                document.htmlUrl(passage.abschnittId()),
                document.pdfUrl());
    }
}
