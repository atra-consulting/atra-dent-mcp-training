package consulting.atra.wissen.data;

import consulting.atra.produktmodell.DataReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GoaeCatalog {

    private final String stand;
    private final String source;
    private final LocalDate fetched;
    private final String hint;
    private final List<GoaePosition> positionen;
    private final Map<String, GoaePosition> byNumber;

    private GoaeCatalog(Datei file) {
        this.stand = file.stand();
        this.source = file.quelle();
        this.fetched = file.abgerufen();
        this.hint = file.hinweis();
        this.positionen = List.copyOf(file.positionen());
        this.byNumber = byNumber(this.positionen);
    }

    public static GoaeCatalog read(Path file) throws IOException {
        return new GoaeCatalog(DataReader.read(file, Datei.class));
    }

    public static GoaeCatalog read(InputStream stream) throws IOException {
        return new GoaeCatalog(DataReader.read(stream, Datei.class));
    }

    public Optional<GoaePosition> position(String nummer) {
        return Optional.ofNullable(byNumber.get(nummer));
    }

    public boolean knowsNumber(String nummer) {
        return byNumber.containsKey(nummer);
    }

    public List<GoaePosition> positionen() {
        return positionen;
    }

    public String stand() {
        return stand;
    }

    public String source() {
        return source;
    }

    public LocalDate fetched() {
        return fetched;
    }

    public String hint() {
        return hint;
    }

    private static Map<String, GoaePosition> byNumber(List<GoaePosition> positionen) {
        Map<String, GoaePosition> map = new LinkedHashMap<>();
        for (GoaePosition position : positionen) {
            if (map.put(position.nummer(), position) != null) {
                throw new IllegalStateException(
                        "Gebuehrennummer doppelt vergeben: " + position.nummer());
            }
        }
        return map;
    }

    private record Datei(
            String stand,
            String quelle,
            LocalDate abgerufen,
            String hinweis,
            List<GoaePosition> positionen) {

        private Datei {
            positionen = positionen == null ? List.of() : new ArrayList<>(positionen);
        }
    }
}
