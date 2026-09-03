package consulting.atra.produktmodell;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

final class ProduktmodellData {

    private static final Path DIRECTORY = Path.of("..", "wissen", "daten");

    private ProduktmodellData() {
    }

    static TarifCatalog tarifCatalog() {
        try {
            return TarifCatalog.read(DIRECTORY.resolve("tarife.yaml"));
        } catch (IOException cause) {
            throw new UncheckedIOException("tarife.yaml is not readable", cause);
        }
    }
}
