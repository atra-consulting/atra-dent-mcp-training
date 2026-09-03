package consulting.atra.wissen.comparison;

import consulting.atra.produktmodell.Leistung;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record LeistungsbereichComparison(
        String schluessel,
        String name,
        String beschreibung,
        Map<String, Leistung> leistungen) {

    public LeistungsbereichComparison {
        leistungen = leistungen == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(leistungen));
    }
}
