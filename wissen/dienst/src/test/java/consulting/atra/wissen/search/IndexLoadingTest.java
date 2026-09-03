package consulting.atra.wissen.search;

import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.documents.GeneratedDocuments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndexLoadingTest {

    private static final String QUESTION_PREFIX = "query: ";
    private static final String PASSAGE_PREFIX = "passage: ";

    @TempDir
    Path directory;

    private final DocumentCatalog catalog = GeneratedDocuments.catalog();
    private final HtmlParser parser = new HtmlParser(
            HtmlParser.DEFAULT_MAX_CHARS, HtmlParser.DEFAULT_OVERLAP);

    @Test
    void aLoadedIndexEmbedsNoPassages() throws Exception {
        Path file = directory.resolve("bedingungen.json");
        BedingungenSearch built = BedingungenSearch.build(
                catalog, parser, new CountingModel(64), QUESTION_PREFIX, PASSAGE_PREFIX);
        built.save(file);

        CountingModel fresh = new CountingModel(64);
        BedingungenSearch loaded = BedingungenSearch.build(
                catalog, parser, fresh, QUESTION_PREFIX, PASSAGE_PREFIX, file);

        assertThat(fresh.calls()).isZero();
        assertThat(loaded.passageCount()).isEqualTo(built.passageCount());
        assertThat(loaded.search("ATRA_DENT_S", "Wie lange ist die Wartezeit?")).isNotEmpty();
    }

    @Test
    void mismatchedIdsTriggerARebuild() throws Exception {
        Path file = directory.resolve("bedingungen.json");
        SimpleVectorStore fremd = SimpleVectorStore.builder(new CountingModel(64)).build();
        fremd.add(List.of(new Document("fremd#abschnitt", "passage: veralteter Stand", Map.of())));
        fremd.save(file.toFile());

        CountingModel fresh = new CountingModel(64);
        BedingungenSearch search = BedingungenSearch.build(
                catalog, parser, fresh, QUESTION_PREFIX, PASSAGE_PREFIX, file);

        assertThat(fresh.calls()).isPositive();
        assertThat(search.passageCount()).isGreaterThan(1);
    }

    @Test
    void aDifferentModelDimensionTriggersARebuild() throws Exception {
        Path file = directory.resolve("bedingungen.json");
        BedingungenSearch.build(catalog, parser, new CountingModel(64),
                QUESTION_PREFIX, PASSAGE_PREFIX).save(file);

        CountingModel narrower = new CountingModel(32);
        BedingungenSearch.build(catalog, parser, narrower,
                QUESTION_PREFIX, PASSAGE_PREFIX, file);

        assertThat(narrower.calls()).isPositive();
    }

    @Test
    void aMissingFileBuildsTheIndexAsBefore() throws Exception {
        CountingModel model = new CountingModel(64);
        BedingungenSearch search = BedingungenSearch.build(catalog, parser, model,
                QUESTION_PREFIX, PASSAGE_PREFIX, directory.resolve("fehlt.json"));

        assertThat(model.calls()).isPositive();
        assertThat(search.passageCount()).isPositive();
    }

    @Test
    void theShippedBedingungenIndexLoadsWithoutEmbedding() throws Exception {
        Path indexFile = GeneratedDocuments.WURZEL.resolve("index").resolve("bedingungen.json");
        CountingModel model = new CountingModel(384);

        BedingungenSearch search = BedingungenSearch.build(
                catalog, parser, model, QUESTION_PREFIX, PASSAGE_PREFIX, indexFile);

        assertThat(model.calls()).isZero();
        assertThat(search.passageCount()).isPositive();
    }
}
