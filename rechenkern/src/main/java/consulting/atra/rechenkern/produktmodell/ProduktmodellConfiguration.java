package consulting.atra.rechenkern.produktmodell;

import consulting.atra.produktmodell.TarifCatalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class ProduktmodellConfiguration {

    private final Path directory;

    ProduktmodellConfiguration(@Value("${rechenkern.data.directory}") String directory) {
        this.directory = Path.of(directory);
    }

    @Bean
    TarifCatalog tarifCatalog() throws IOException {
        return TarifCatalog.read(directory.resolve("tarife.yaml"));
    }
}
