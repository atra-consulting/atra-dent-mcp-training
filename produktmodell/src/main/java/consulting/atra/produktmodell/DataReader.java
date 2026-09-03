package consulting.atra.produktmodell;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DataReader {

    private static final ObjectMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private DataReader() {
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            return read(stream, type);
        }
    }

    public static <T> T read(InputStream stream, Class<T> type) throws IOException {
        T content = MAPPER.readValue(stream, type);
        if (content == null) {
            throw new IOException("The data file is empty: expected " + type.getSimpleName());
        }
        return content;
    }
}
