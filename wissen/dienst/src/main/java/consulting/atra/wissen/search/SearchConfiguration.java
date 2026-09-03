package consulting.atra.wissen.search;

import consulting.atra.wissen.documents.DocumentCatalog;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class SearchConfiguration {

    @Bean
    HtmlParser htmlParser(
            @Value("${wissen.search.max-chars:" + HtmlParser.DEFAULT_MAX_CHARS + "}")
            int maxChars,
            @Value("${wissen.search.overlap:" + HtmlParser.DEFAULT_OVERLAP + "}")
            int overlap) {
        return new HtmlParser(maxChars, overlap);
    }

    @Bean
    BedingungenSearch bedingungenSearch(
            DocumentCatalog catalog,
            HtmlParser parser,
            EmbeddingModel model,
            @Value("${wissen.search.question-prefix:query: }") String questionPrefix,
            @Value("${wissen.search.passage-prefix:passage: }") String passagePrefix,
            @Value("${wissen.documents.directory}") String documentsDirectory,
            @Value("${wissen.index.load:true}") boolean loadIndex)
            throws IOException {
        Path indexFile = loadIndex
                ? Path.of(documentsDirectory, "index", "bedingungen.json")
                : null;
        return BedingungenSearch.build(catalog, parser, model, questionPrefix, passagePrefix,
                indexFile);
    }
}
