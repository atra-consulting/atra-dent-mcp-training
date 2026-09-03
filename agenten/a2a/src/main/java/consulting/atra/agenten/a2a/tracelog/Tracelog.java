package consulting.atra.agenten.a2a.tracelog;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Tracelog {

    public static final String ENV_VARIABLE = "AGENTEN_TRACELOG";

    public static final Tracelog OFF = new Tracelog(null, null);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;
    private final String service;
    private boolean failed;

    private Tracelog(Path file, String service) {
        this.file = file;
        this.service = service;
    }

    public static Tracelog fromEnvironment(String service) {
        return forDirectory(System.getenv(ENV_VARIABLE), service);
    }

    public static Tracelog forDirectory(String directory, String service) {
        Objects.requireNonNull(service, "service");
        if (directory == null || directory.isBlank()) {
            return OFF;
        }
        Path target = Path.of(directory.trim());
        try {
            Files.createDirectories(target);
        } catch (IOException exception) {
            System.err.println("Tracelog: " + target + " could not be created ("
                    + exception.getMessage() + ") - nothing will be recorded.");
            return OFF;
        }
        return new Tracelog(target.resolve(service + ".jsonl"), service);
    }

    public boolean isEnabled() {
        return file != null && !failed;
    }

    public synchronized void write(String kind, String context, Map<String, Object> fields) {
        if (!isEnabled()) {
            return;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("timestamp", Instant.now().toString());
        line.put("service", service);
        line.put("kind", kind);
        if (context != null) {
            line.put("context", context);
        }
        if (fields != null) {
            line.putAll(fields);
        }
        try {
            Files.writeString(file, JSON.writeValueAsString(line) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            failed = true;
            System.err.println("Tracelog: writing to " + file + " failed ("
                    + exception.getMessage() + ") - nothing will be recorded from here on.");
        }
    }

    public Path file() {
        return file;
    }
}
