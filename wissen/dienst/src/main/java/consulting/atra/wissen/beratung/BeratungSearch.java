package consulting.atra.wissen.beratung;

import consulting.atra.wissen.documents.CatalogDocument;
import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.documents.Confidentiality;
import consulting.atra.wissen.search.HtmlParser;
import consulting.atra.wissen.search.IndexFile;
import consulting.atra.wissen.search.Passage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BeratungSearch {

    public static final int DEFAULT_COUNT = 5;

    public static final int DEFAULT_MAX_CHARS = 400;

    public static final int DEFAULT_OVERLAP = 80;

    private static final int SUPERSEDING = 4;

    private static final Logger LOG = LoggerFactory.getLogger(BeratungSearch.class);

    private final SimpleVectorStore store;
    private final Map<String, Passage> passages;
    private final Set<String> internalDocuments;
    private final String questionPrefix;

    private BeratungSearch(SimpleVectorStore store, Map<String, Passage> passages,
                           Set<String> interneDokumente, String questionPrefix) {
        this.store = store;
        this.passages = passages;
        this.internalDocuments = interneDokumente;
        this.questionPrefix = questionPrefix;
    }

    public static BeratungSearch build(DocumentCatalog catalog, HtmlParser parser,
                                          EmbeddingModel model, String questionPrefix,
                                          String passagePrefix) throws IOException {
        return build(catalog, parser, model, questionPrefix, passagePrefix, null);
    }

    public static BeratungSearch build(DocumentCatalog catalog, HtmlParser parser,
                                          EmbeddingModel model, String questionPrefix,
                                          String passagePrefix, Path indexFile) throws IOException {
        List<CatalogDocument> intern = catalog.documents(Confidentiality.INTERN);
        if (intern.isEmpty()) {
            throw new IOException("Kein internes Beratungsmaterial im Dokumentkatalog. Erwartet wird "
                    + "mindestens ein Dokument der Stufe " + Confidentiality.INTERN
                    + "; bitte ./wissen/build.sh ausfuehren.");
        }

        Map<String, Passage> found = new LinkedHashMap<>();
        Map<String, String> expectedTexts = new LinkedHashMap<>();
        List<Document> toEmbed = new ArrayList<>();
        Set<String> documentIds = new LinkedHashSet<>();

        for (CatalogDocument document : intern) {
            documentIds.add(document.dokumentId());
            for (Passage passage : parser.split(document.dokumentId(), document.htmlDatei())) {
                found.put(passage.id(), passage);
                String text = passagePrefix + passage.embeddingText();
                expectedTexts.put(passage.id(), text);
                toEmbed.add(new Document(
                        passage.id(),
                        text,
                        Map.of()));
            }
        }

        SimpleVectorStore store = SimpleVectorStore.builder(model).build();
        if (IndexFile.load(store, indexFile, expectedTexts, model)) {
            LOG.info("Beratungssuche bereit: {} Passagen aus {} geladen, "
                    + "eigener Index getrennt von den Bedingungen", found.size(), indexFile);
        } else {
            store.add(toEmbed);
            LOG.info("Beratungssuche bereit: {} Passagen aus {} internen Dokumenten eingebettet, "
                    + "eigener Index getrennt von den Bedingungen", toEmbed.size(), intern.size());
        }

        return new BeratungSearch(store, Map.copyOf(found),
                Set.copyOf(documentIds), questionPrefix);
    }

    public List<BeratungHit> search(String question) {
        return search(question, DEFAULT_COUNT);
    }

    public List<BeratungHit> search(String question, int count) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Die Frage darf nicht leer sein.");
        }
        if (count < 1) {
            throw new IllegalArgumentException("anzahl muss mindestens 1 sein, war " + count);
        }

        List<Document> found = store.similaritySearch(
                SearchRequest.builder()
                        .query(questionPrefix + question.strip())
                        .similarityThresholdAll()
                        .topK(count * SUPERSEDING)
                        .build());

        if (found == null) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<BeratungHit> hits = new ArrayList<>();
        for (Document match : found) {
            Passage passage = passages.get(match.getId());
            if (passage == null || !internalDocuments.contains(passage.dokumentId())) {
                continue;
            }
            if (!seen.add(passage.abschnittId())) {
                continue;
            }
            hits.add(BeratungHit.aus(passage, bewertung(match.getScore())));
            if (hits.size() == count) {
                break;
            }
        }
        return List.copyOf(hits);
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        store.save(file.toFile());
    }

    public int passageCount() {
        return passages.size();
    }

    public Set<String> documentIds() {
        return internalDocuments;
    }

    private static double bewertung(Double kosinus) {
        return kosinus == null ? 0.0 : Math.clamp(kosinus, 0.0, 1.0);
    }
}
