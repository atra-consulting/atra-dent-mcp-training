package consulting.atra.wissen.beratung;

import consulting.atra.produktmodell.TarifCatalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestLeitfaden {

    private static final Path DIRECTORY = Path.of("..", "daten");

    private TestLeitfaden() {
    }

    static TarifCatalog tarifCatalog() {
        return read(DIRECTORY.resolve("tarife.yaml"), TarifCatalog::read);
    }

    static LeitfadenCatalog leitfadenCatalog() {
        return read(DIRECTORY.resolve("beratung").resolve("leitfaden.yaml"),
                LeitfadenCatalog::read);
    }

    static Tarifempfehlung recommendation() {
        return new Tarifempfehlung(tarifCatalog(), leitfadenCatalog());
    }

    private static <T> T read(Path file, Leser<T> leser) {
        if (!Files.isReadable(file)) {
            throw new IllegalStateException(
                    "Datendatei nicht lesbar: " + file.toAbsolutePath().normalize());
        }
        try {
            return leser.read(file);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    @FunctionalInterface
    private interface Leser<T> {
        T read(Path file) throws IOException;
    }
}
