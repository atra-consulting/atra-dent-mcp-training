package consulting.atra.wissen;

import consulting.atra.wissen.beratung.BeratungSearch;
import consulting.atra.wissen.search.BedingungenSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@Profile("indexbau")
class IndexBuildConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(IndexBuildConfiguration.class);

    @Bean
    ApplicationRunner writeIndex(
            BedingungenSearch bedingungenSearch,
            BeratungSearch beratungSearch,
            @Value("${wissen.documents.directory}") String documentsDirectory) {
        return argumente -> {
            Path indexDirectory = Path.of(documentsDirectory, "index");
            Path conditions = indexDirectory.resolve("bedingungen.json");
            Path beratung = indexDirectory.resolve("beratung.json");
            bedingungenSearch.save(conditions);
            beratungSearch.save(beratung);
            LOG.info("Indizes geschrieben: {} ({} Passagen) und {} ({} Passagen)",
                    conditions, bedingungenSearch.passageCount(),
                    beratung, beratungSearch.passageCount());
        };
    }
}
