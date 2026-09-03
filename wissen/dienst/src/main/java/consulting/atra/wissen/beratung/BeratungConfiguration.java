package consulting.atra.wissen.beratung;

import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.search.HtmlParser;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class BeratungConfiguration {

    private final Path directory;

    BeratungConfiguration(@Value("${wissen.data.directory}") String directory) {
        this.directory = Path.of(directory);
    }

    @Bean
    LeitfadenCatalog leitfadenCatalog() throws IOException {
        return LeitfadenCatalog.read(directory.resolve("beratung").resolve("leitfaden.yaml"));
    }

    @Bean
    BeratungSearch beratungSearch(
            DocumentCatalog catalog,
            EmbeddingModel model,
            @Value("${wissen.beratung.search.max-chars:"
                    + BeratungSearch.DEFAULT_MAX_CHARS + "}") int maxChars,
            @Value("${wissen.beratung.search.overlap:"
                    + BeratungSearch.DEFAULT_OVERLAP + "}") int overlap,
            @Value("${wissen.search.question-prefix:query: }") String questionPrefix,
            @Value("${wissen.search.passage-prefix:passage: }") String passagePrefix,
            @Value("${wissen.documents.directory}") String documentsDirectory,
            @Value("${wissen.index.load:true}") boolean loadIndex)
            throws IOException {
        Path indexFile = loadIndex
                ? Path.of(documentsDirectory, "index", "beratung.json")
                : null;
        return BeratungSearch.build(catalog, new HtmlParser(maxChars, overlap),
                model, questionPrefix, passagePrefix, indexFile);
    }
}
