package consulting.atra.agenten.beratung.beleg;

import consulting.atra.agenten.mcp.ObservedTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class BelegCollector {

    public static final String BEDINGUNGEN = "bedingungen_suchen";

    private static final Logger log = LoggerFactory.getLogger(BelegCollector.class);

    private final ObjectMapper mapper;

    public BelegCollector(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
    }

    public List<Map<String, Object>> collect(ObservedTools observed) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> belege = new ArrayList<>();

        for (String rohergebnis : observed.resultsOf(BEDINGUNGEN)) {
            for (JsonNode treffer : hits(rohergebnis)) {
                String key = text(treffer, "dokumentId") + "#" + text(treffer, "abschnittId");
                if (seen.add(key)) {
                    belege.add(beleg(treffer));
                }
            }
        }
        return List.copyOf(belege);
    }

    private List<JsonNode> hits(String rohergebnis) {
        try {
            JsonNode root = mapper.readTree(rohergebnis);
            JsonNode hits = root.get("treffer");
            if (hits == null || !hits.isArray()) {
                return List.of();
            }
            List<JsonNode> alle = new ArrayList<>();
            hits.forEach(alle::add);
            return alle;
        } catch (RuntimeException exception) {
            log.warn("Ergebnis von {} liess sich nicht lesen; Beleg entfaellt",
                    BEDINGUNGEN, exception);
            return List.of();
        }
    }

    private static Map<String, Object> beleg(JsonNode treffer) {
        Map<String, Object> beleg = new LinkedHashMap<>();
        beleg.put("dokumentId", text(treffer, "dokumentId"));
        beleg.put("abschnittId", text(treffer, "abschnittId"));
        beleg.put("ueberschrift", text(treffer, "ueberschrift"));
        beleg.put("htmlUrl", text(treffer, "htmlUrl"));
        beleg.put("pdfUrl", text(treffer, "pdfUrl"));
        return beleg;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
