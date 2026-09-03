package consulting.atra.wissen.documents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class GeneratedDocuments {

    public static final Path WURZEL = Path.of("..", "generated");

    private GeneratedDocuments() {
    }

    public static DocumentCatalog catalog() {
        try {
            return DocumentCatalog.read(WURZEL);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
