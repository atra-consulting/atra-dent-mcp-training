package consulting.atra.agenten.orchestrator.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArztauskunftCollector {

    public static final String SOURCE = "source";

    public static final String RENDERINGS = "views";

    private final Map<String, Object> content = new LinkedHashMap<>();

    public void adopt(String agent, Map<String, Object> data) {
        content.clear();
        content.put(SOURCE, agent);
        if (data != null) {
            content.putAll(data);
        }
    }

    public void collectView(Map<String, Object> rendering) {
        List<Object> sofar = new ArrayList<>();
        if (content.get(RENDERINGS) instanceof List<?> vorhanden) {
            sofar.addAll(vorhanden);
        }
        sofar.add(Map.copyOf(rendering));
        content.put(RENDERINGS, List.copyOf(sofar));
    }

    public Map<String, Object> content() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(content));
    }
}
