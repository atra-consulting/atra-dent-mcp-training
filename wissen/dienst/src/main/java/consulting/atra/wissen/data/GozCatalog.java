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

public final class GozCatalog {

    private final String stand;
    private final String source;
    private final LocalDate fetched;
    private final String hint;
    private final List<GozPosition> positionen;
    private final Map<String, GozPosition> byNumber;
    private final Map<String, List<GozPosition>> bySection;
    private final List<SectionCoverage> coverage;
    private final List<LeistungsbereichWithoutPosition> areasWithoutPosition;
    private final List<PositionWithoutNummer> ohneGebuehrennummer;

    private GozCatalog(Datei file) {
        this.stand = file.stand();
        this.source = file.quelle();
        this.fetched = file.abgerufen();
        this.hint = file.hinweis();
        this.positionen = List.copyOf(file.positionen());
        this.byNumber = byNumber(this.positionen);
        this.bySection = bySection(this.positionen);
        this.coverage = List.copyOf(file.abdeckung());
        this.areasWithoutPosition = List.copyOf(file.bereicheOhnePosition());
        this.ohneGebuehrennummer = List.copyOf(file.ohneGebuehrennummer());
    }

    public static GozCatalog read(Path file) throws IOException {
        return new GozCatalog(DataReader.read(file, Datei.class));
    }

    public static GozCatalog read(InputStream stream) throws IOException {
        return new GozCatalog(DataReader.read(stream, Datei.class));
    }

    public Optional<GozPosition> position(String nummer) {
        return Optional.ofNullable(byNumber.get(nummer));
    }

    public boolean knowsNumber(String nummer) {
        return byNumber.containsKey(nummer);
    }

    public List<GozPosition> positionen() {
        return positionen;
    }

    public Map<String, List<GozPosition>> positionenPerSection() {
        return bySection;
    }

    public List<SectionCoverage> coverage() {
        return coverage;
    }

    public List<LeistungsbereichWithoutPosition> areasWithoutPosition() {
        return areasWithoutPosition;
    }

    public List<PositionWithoutNummer> ohneGebuehrennummer() {
        return ohneGebuehrennummer;
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

    private static Map<String, GozPosition> byNumber(List<GozPosition> positionen) {
        Map<String, GozPosition> map = new LinkedHashMap<>();
        for (GozPosition position : positionen) {
            if (map.put(position.nummer(), position) != null) {
                throw new IllegalStateException(
                        "Gebuehrennummer doppelt vergeben: " + position.nummer());
            }
        }
        return map;
    }

    private static Map<String, List<GozPosition>> bySection(List<GozPosition> positionen) {
        Map<String, List<GozPosition>> map = new LinkedHashMap<>();
        for (GozPosition position : positionen) {
            map.computeIfAbsent(position.abschnitt(), a -> new ArrayList<>()).add(position);
        }
        map.replaceAll((abschnitt, liste) -> List.copyOf(liste));
        return Map.copyOf(map);
    }

    private record Datei(
            String stand,
            String quelle,
            LocalDate abgerufen,
            String hinweis,
            List<SectionCoverage> abdeckung,
            List<LeistungsbereichWithoutPosition> bereicheOhnePosition,
            List<PositionWithoutNummer> ohneGebuehrennummer,
            List<GozPosition> positionen) {

        private Datei {
            abdeckung = abdeckung == null ? List.of() : new ArrayList<>(abdeckung);
            bereicheOhnePosition = bereicheOhnePosition == null
                    ? List.of() : new ArrayList<>(bereicheOhnePosition);
            ohneGebuehrennummer = ohneGebuehrennummer == null
                    ? List.of() : new ArrayList<>(ohneGebuehrennummer);
            positionen = positionen == null ? List.of() : new ArrayList<>(positionen);
        }
    }
}
