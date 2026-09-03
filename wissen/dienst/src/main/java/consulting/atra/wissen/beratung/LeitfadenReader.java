package consulting.atra.wissen.beratung;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class LeitfadenReader {

    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private LeitfadenReader() {
    }

    static <T> T read(Path file, Class<T> typ) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            return read(stream, typ);
        }
    }

    static <T> T read(InputStream stream, Class<T> typ) throws IOException {
        T content = MAPPER.readValue(stream, typ);
        if (content == null) {
            throw new IOException("Die Leitfadendatei ist leer: erwartet wurde " + typ.getSimpleName());
        }
        return content;
    }
}
