package consulting.atra.wissen.documents;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DocumentCatalog {

    private static final Set<String> ALLE_TARIFE =
            Set.of("ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");

    private static final List<Bauplan> BLUEPRINTS = List.of(
            new Bauplan("atra-dent-smart-avb", DocumentType.BEDINGUNGSWERK,
                    Confidentiality.OEFFENTLICH, "ATRA_DENT_S", Set.of("ATRA_DENT_S")),
            new Bauplan("atra-dent-balance-avb", DocumentType.BEDINGUNGSWERK,
                    Confidentiality.OEFFENTLICH, "ATRA_DENT_B", Set.of("ATRA_DENT_B")),
            new Bauplan("atra-dent-brillant-avb", DocumentType.BEDINGUNGSWERK,
                    Confidentiality.OEFFENTLICH, "ATRA_DENT_X",
                    Set.of("ATRA_DENT_X", "ATRA_DENT_X_SB")),
            new Bauplan("atra-dent-tarifvergleich", DocumentType.TARIFVERGLEICH,
                    Confidentiality.OEFFENTLICH, null, ALLE_TARIFE),
            new Bauplan("atra-dent-goz-zuordnung", DocumentType.GOZ_ZUORDNUNG,
                    Confidentiality.OEFFENTLICH, null, ALLE_TARIFE),
            new Bauplan("atra-dent-beratungshandbuch", DocumentType.BERATUNGSHANDBUCH,
                    Confidentiality.INTERN, null, ALLE_TARIFE));

    private final Map<String, CatalogDocument> documents;

    private DocumentCatalog(Map<String, CatalogDocument> dokumente) {
        this.documents = dokumente;
    }

    public static DocumentCatalog read(Path wurzel) throws IOException {
        Path html = wurzel.resolve("html");
        Path pdf = wurzel.resolve("pdf");
        if (!Files.isDirectory(html) || !Files.isDirectory(pdf)) {
            throw new IOException(
                    "Die erzeugten Dokumente fehlen: erwartet werden die Verzeichnisse pdf/ und html/ unter "
                            + wurzel.toAbsolutePath().normalize()
                            + ". Bitte ./wissen/build.sh ausfuehren.");
        }

        Map<String, CatalogDocument> read = new LinkedHashMap<>();
        for (Bauplan bauplan : BLUEPRINTS) {
            Path htmlFile = html.resolve(bauplan.dokumentId() + ".html");
            Path pdfFile = pdf.resolve(bauplan.dokumentId() + ".pdf");
            checkReadable(htmlFile);
            checkReadable(pdfFile);
            read.put(bauplan.dokumentId(), new CatalogDocument(
                    bauplan.dokumentId(),
                    readTitle(htmlFile),
                    bauplan.art(),
                    bauplan.vertraulichkeit(),
                    Optional.ofNullable(bauplan.tarif()),
                    bauplan.gueltigFuer(),
                    pdfFile,
                    htmlFile));
        }
        return new DocumentCatalog(Collections.unmodifiableMap(read));
    }

    public List<CatalogDocument> documents() {
        return List.copyOf(documents.values());
    }

    public List<CatalogDocument> documents(Confidentiality stufe) {
        Objects.requireNonNull(stufe, "stufe");
        return documents.values().stream()
                .filter(dokument -> dokument.vertraulichkeit() == stufe)
                .toList();
    }

    public Optional<CatalogDocument> document(String dokumentId) {
        return Optional.ofNullable(documents.get(dokumentId));
    }

    public List<CatalogDocument> fuerTarif(String tarifschluessel) {
        checkTarif(tarifschluessel);
        return documents.values().stream()
                .filter(dokument -> dokument.giltFuer(tarifschluessel))
                .toList();
    }

    public List<CatalogDocument> fuerTarif(String tarifschluessel, Confidentiality stufe) {
        Objects.requireNonNull(stufe, "stufe");
        checkTarif(tarifschluessel);
        return documents.values().stream()
                .filter(dokument -> dokument.giltFuer(tarifschluessel))
                .filter(dokument -> dokument.vertraulichkeit() == stufe)
                .toList();
    }

    public Optional<CatalogDocument> bedingungswerk(String tarifschluessel) {
        return fuerTarif(tarifschluessel).stream()
                .filter(dokument -> dokument.art() == DocumentType.BEDINGUNGSWERK)
                .findFirst();
    }

    private static void checkTarif(String tarifschluessel) {
        if (!ALLE_TARIFE.contains(tarifschluessel)) {
            throw new IllegalArgumentException("Tarif '" + tarifschluessel
                    + "' ist nicht bekannt. Gueltig sind "
                    + String.join(", ", ALLE_TARIFE.stream().sorted().toList()));
        }
    }

    private static void checkReadable(Path file) throws IOException {
        if (!Files.isReadable(file)) {
            throw new IOException("Erzeugtes Dokument fehlt oder ist nicht lesbar: "
                    + file.toAbsolutePath().normalize()
                    + ". Bitte ./wissen/build.sh ausfuehren.");
        }
    }

    private static String readTitle(Path htmlDatei) throws IOException {
        String title = GeneratedText.normalize(
                Jsoup.parse(htmlDatei, StandardCharsets.UTF_8.name()).title());
        if (title.isEmpty()) {
            throw new IOException("Die HTML-Fassung hat keinen Titel: "
                    + htmlDatei.toAbsolutePath().normalize());
        }
        return title;
    }

    private record Bauplan(
            String dokumentId,
            DocumentType art,
            Confidentiality vertraulichkeit,
            String tarif,
            Set<String> gueltigFuer) {
    }
}
