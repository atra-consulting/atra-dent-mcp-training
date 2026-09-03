package consulting.atra.wissen.data;

import consulting.atra.produktmodell.TarifCatalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestData {

    private static final Path DIRECTORY = Path.of("..", "daten");

    private TestData() {
    }

    static TarifCatalog tarifCatalog() {
        return read("tarife.yaml", TarifCatalog::read);
    }

    static GozCatalog gozCatalog() {
        return read("goz-zuordnung.yaml", GozCatalog::read);
    }

    static GoaeCatalog goaeCatalog() {
        return read("goae-auszug.yaml", GoaeCatalog::read);
    }

    private static <T> T read(String filename, Leser<T> leser) {
        Path file = DIRECTORY.resolve(filename);
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
