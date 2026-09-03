package consulting.atra.wissen.data;

import consulting.atra.produktmodell.TarifCatalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class DataConfiguration {

    private final Path directory;

    DataConfiguration(@Value("${wissen.data.directory}") String directory) {
        this.directory = Path.of(directory);
    }

    @Bean
    TarifCatalog tarifCatalog() throws IOException {
        return TarifCatalog.read(directory.resolve("tarife.yaml"));
    }

    @Bean
    GozCatalog gozCatalog() throws IOException {
        return GozCatalog.read(directory.resolve("goz-zuordnung.yaml"));
    }

    @Bean
    GoaeCatalog goaeCatalog() throws IOException {
        return GoaeCatalog.read(directory.resolve("goae-auszug.yaml"));
    }
}
