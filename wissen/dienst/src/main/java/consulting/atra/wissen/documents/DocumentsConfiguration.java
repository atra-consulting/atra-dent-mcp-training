package consulting.atra.wissen.documents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class DocumentsConfiguration {

    @Bean
    DocumentCatalog documentCatalog(
            @Value("${wissen.documents.directory}") String directory) throws IOException {
        return DocumentCatalog.read(Path.of(directory));
    }
}
