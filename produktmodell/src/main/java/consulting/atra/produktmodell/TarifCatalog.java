package consulting.atra.produktmodell;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TarifCatalog {

    private final LocalDate asOf;
    private final List<LeistungsbereichDescription> leistungsbereiche;
    private final List<String> leistungsbereichKeys;
    private final Map<String, Tarif> tarife;
    private final Map<String, Staffel> staffeln;
    private final List<Ausschluss> exclusions;

    private TarifCatalog(Data data) {
        this.asOf = data.stand();
        this.leistungsbereiche = List.copyOf(data.leistungsbereiche());
        this.leistungsbereichKeys = this.leistungsbereiche.stream()
                .map(LeistungsbereichDescription::schluessel)
                .toList();
        this.tarife = resolve(data.tarife());
        this.staffeln = byKey(data.staffeln());
        this.exclusions = List.copyOf(data.ausschluesse());
    }

    public static TarifCatalog read(Path file) throws IOException {
        return new TarifCatalog(DataReader.read(file, Data.class));
    }

    public static TarifCatalog read(InputStream stream) throws IOException {
        return new TarifCatalog(DataReader.read(stream, Data.class));
    }

    public LocalDate asOf() {
        return asOf;
    }

    public Optional<Tarif> tarif(String key) {
        return Optional.ofNullable(tarife.get(key));
    }

    public List<String> tarifKeys() {
        return List.copyOf(tarife.keySet());
    }

    public List<Tarif> tarife() {
        return List.copyOf(tarife.values());
    }

    public Optional<Leistung> findLeistung(String tarifKey, String leistungsbereich) {
        return tarif(tarifKey).flatMap(t -> t.findLeistung(leistungsbereich));
    }

    public List<LeistungsbereichDescription> leistungsbereiche() {
        return leistungsbereiche;
    }

    public List<String> leistungsbereichKeys() {
        return leistungsbereichKeys;
    }

    public Optional<Staffel> staffel(String key) {
        return Optional.ofNullable(staffeln.get(key));
    }

    public List<Ausschluss> exclusions() {
        return exclusions;
    }

    private static Map<String, Tarif> resolve(List<Tarif> read) {
        Map<String, Tarif> byKey = new LinkedHashMap<>();
        for (Tarif tarif : read) {
            if (byKey.put(tarif.schluessel(), tarif) != null) {
                throw new IllegalStateException(
                        "Duplicate Tarif key: " + tarif.schluessel());
            }
        }

        Map<String, Tarif> resolved = new LinkedHashMap<>();
        for (Tarif tarif : byKey.values()) {
            resolved.put(tarif.schluessel(),
                    tarif.withLeistungen(collectLeistungen(tarif, byKey)));
        }
        return resolved;
    }

    private static Map<String, Leistung> collectLeistungen(Tarif start, Map<String, Tarif> all) {
        Set<String> visited = new LinkedHashSet<>();
        Tarif current = start;
        while (current.leistungen() == null) {
            if (current.leistungenWie() == null) {
                throw new IllegalStateException(
                        "Tarif " + current.schluessel() + " has neither leistungen nor leistungen_wie");
            }
            if (!visited.add(current.schluessel())) {
                throw new IllegalStateException(
                        "leistungen_wie forms a cycle: " + String.join(" -> ", visited));
            }
            Tarif referenced = all.get(current.leistungenWie());
            if (referenced == null) {
                throw new IllegalStateException("Tarif " + current.schluessel()
                        + " references an unknown Tarif via leistungen_wie: "
                        + current.leistungenWie());
            }
            current = referenced;
        }
        return Map.copyOf(current.leistungen());
    }

    private static Map<String, Staffel> byKey(List<Staffel> staffeln) {
        Map<String, Staffel> map = new LinkedHashMap<>();
        for (Staffel staffel : staffeln) {
            if (map.put(staffel.schluessel(), staffel) != null) {
                throw new IllegalStateException(
                        "Duplicate Staffel key: " + staffel.schluessel());
            }
        }
        return map;
    }

    private record Data(
            LocalDate stand,
            List<LeistungsbereichDescription> leistungsbereiche,
            List<Tarif> tarife,
            List<Staffel> staffeln,
            List<Ausschluss> ausschluesse) {

        private Data {
            leistungsbereiche = leistungsbereiche == null ? List.of() : new ArrayList<>(leistungsbereiche);
            tarife = tarife == null ? List.of() : new ArrayList<>(tarife);
            staffeln = staffeln == null ? List.of() : new ArrayList<>(staffeln);
            ausschluesse = ausschluesse == null ? List.of() : new ArrayList<>(ausschluesse);
        }
    }
}
