package consulting.atra.wissen.search;

import consulting.atra.wissen.documents.CatalogDocument;
import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.documents.Confidentiality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BedingungenSearch {

    public static final int DEFAULT_COUNT = 5;

    private static final int SUPERSEDING = 4;

    private static final String KEY_DOCUMENT = "dokumentId";

    private static final Logger LOG = LoggerFactory.getLogger(BedingungenSearch.class);

    private final DocumentCatalog catalog;
    private final SimpleVectorStore store;
    private final Map<String, Passage> passages;
    private final String questionPrefix;

    private BedingungenSearch(DocumentCatalog catalog, SimpleVectorStore store,
                            Map<String, Passage> passages, String questionPrefix) {
        this.catalog = catalog;
        this.store = store;
        this.passages = passages;
        this.questionPrefix = questionPrefix;
    }

    public static BedingungenSearch build(DocumentCatalog catalog, HtmlParser parser,
                                           EmbeddingModel model, String questionPrefix,
                                           String passagePrefix) throws IOException {
        return build(catalog, parser, model, questionPrefix, passagePrefix, null);
    }

    public static BedingungenSearch build(DocumentCatalog catalog, HtmlParser parser,
                                           EmbeddingModel model, String questionPrefix,
                                           String passagePrefix, Path indexFile) throws IOException {
        Map<String, Passage> found = new LinkedHashMap<>();
        Map<String, String> expectedTexts = new LinkedHashMap<>();
        List<Document> toEmbed = new ArrayList<>();

        for (CatalogDocument document : catalog.documents(Confidentiality.OEFFENTLICH)) {
            for (Passage passage : parser.split(document.dokumentId(), document.htmlDatei())) {
                found.put(passage.id(), passage);
                String text = passagePrefix + passage.embeddingText();
                expectedTexts.put(passage.id(), text);
                toEmbed.add(new Document(
                        passage.id(),
                        text,
                        Map.of(KEY_DOCUMENT, passage.dokumentId())));
            }
        }

        SimpleVectorStore store = SimpleVectorStore.builder(model).build();
        if (IndexFile.load(store, indexFile, expectedTexts, model)) {
            LOG.info("Bedingungssuche bereit: {} Passagen aus {} geladen",
                    found.size(), indexFile);
        } else {
            store.add(toEmbed);
            LOG.info("Bedingungssuche bereit: {} Passagen aus {} veroeffentlichten Dokumenten eingebettet",
                    toEmbed.size(), catalog.documents(Confidentiality.OEFFENTLICH).size());
        }

        return new BedingungenSearch(catalog, store, Map.copyOf(found), questionPrefix);
    }

    public List<Hit> search(String tarifschluessel, String question) {
        return search(tarifschluessel, question, DEFAULT_COUNT);
    }

    public List<Hit> search(String tarifschluessel, String question, int count) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Die Frage darf nicht leer sein.");
        }
        if (count < 1) {
            throw new IllegalArgumentException("anzahl muss mindestens 1 sein, war " + count);
        }

        Map<String, CatalogDocument> allowed = new LinkedHashMap<>();
        for (CatalogDocument document : catalog.fuerTarif(tarifschluessel, Confidentiality.OEFFENTLICH)) {
            allowed.put(document.dokumentId(), document);
        }

        List<Document> found = store.similaritySearch(
                SearchRequest.builder()
                        .query(questionPrefix + question.strip())
                        .similarityThresholdAll()
                        .filterExpression(onlyDocuments(allowed.keySet()))
                        .topK(count * SUPERSEDING)
                        .build());

        if (found == null) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<Hit> hits = new ArrayList<>();
        for (Document match : found) {
            Passage passage = passages.get(match.getId());
            if (passage == null) {
                continue;
            }
            CatalogDocument document = allowed.get(passage.dokumentId());
            if (document == null || !seen.add(passage.dokumentId() + "#" + passage.abschnittId())) {
                continue;
            }
            hits.add(Hit.of(passage, document, bewertung(match.getScore())));
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

    private static Filter.Expression onlyDocuments(Set<String> dokumentIds) {
        return new FilterExpressionBuilder()
                .in(KEY_DOCUMENT, dokumentIds.toArray())
                .build();
    }

    private static double bewertung(Double kosinus) {
        return kosinus == null ? 0.0 : Math.clamp(kosinus, 0.0, 1.0);
    }
}
