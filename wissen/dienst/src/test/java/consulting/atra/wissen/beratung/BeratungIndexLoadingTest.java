package consulting.atra.wissen.beratung;

import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.documents.GeneratedDocuments;
import consulting.atra.wissen.search.HtmlParser;
import consulting.atra.wissen.search.CountingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BeratungIndexLoadingTest {

    private static final String QUESTION_PREFIX = "query: ";
    private static final String PASSAGE_PREFIX = "passage: ";

    @TempDir
    Path directory;

    private final DocumentCatalog catalog = GeneratedDocuments.catalog();
    private final HtmlParser parser = new HtmlParser(
            BeratungSearch.DEFAULT_MAX_CHARS, BeratungSearch.DEFAULT_OVERLAP);

    @Test
    void aLoadedIndexEmbedsNoPassages() throws Exception {
        Path file = directory.resolve("beratung.json");
        BeratungSearch built = BeratungSearch.build(
                catalog, parser, new CountingModel(64), QUESTION_PREFIX, PASSAGE_PREFIX);
        built.save(file);

        CountingModel fresh = new CountingModel(64);
        BeratungSearch loaded = BeratungSearch.build(
                catalog, parser, fresh, QUESTION_PREFIX, PASSAGE_PREFIX, file);

        assertThat(fresh.calls()).isZero();
        assertThat(loaded.passageCount()).isEqualTo(built.passageCount());
        assertThat(loaded.search("Wie gehe ich mit einem Preiseinwand um?")).isNotEmpty();
    }

    @Test
    void mismatchedIdsTriggerARebuild() throws Exception {
        Path file = directory.resolve("beratung.json");
        SimpleVectorStore fremd = SimpleVectorStore.builder(new CountingModel(64)).build();
        fremd.add(List.of(new Document("fremd#abschnitt", "passage: veralteter Stand", Map.of())));
        fremd.save(file.toFile());

        CountingModel fresh = new CountingModel(64);
        BeratungSearch search = BeratungSearch.build(
                catalog, parser, fresh, QUESTION_PREFIX, PASSAGE_PREFIX, file);

        assertThat(fresh.calls()).isPositive();
        assertThat(search.passageCount()).isGreaterThan(1);
    }
}
