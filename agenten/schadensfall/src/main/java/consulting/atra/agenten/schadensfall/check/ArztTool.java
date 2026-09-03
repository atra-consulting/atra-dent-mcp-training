package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.a2a.client.AgentClient;
import consulting.atra.agenten.a2a.client.AgentUnreachableException;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ArztTool implements ToolCallback {

    public static final String ERROR = "fehler";

    public static final String SKILL = "rechnung-beurteilen";

    static final String QUESTION = "Bitte beurteilen Sie die Positionen dieser Rechnung.";

    private static final String DESCRIPTION = """
            Holt die Fachauskunft des Arztservice zu den Positionen dieser
            Rechnung: ob sie zusammen eine schluessige Behandlung ergeben
            (plausibilitaet) und ob eine solche Behandlung ueblich ist
            (notwendigkeit), dazu ein Text und ein Hinweis.

            Die Positionen und das Behandlungsdatum nimmt das Tool selbst aus
            der Akte -- du brauchst nichts mitzugeben.

            WICHTIG: Die Auskunft ist eine Sprachmodell-Auskunft, kein Beleg.
            Sie ist nicht geprueft und stuetzt sich auf keine Quelle. Sie sagt
            auch nichts darueber, ob eine Position versichert ist
            oder was erstattet wird. Nimm sie als Hinweis fuer eine Eskalation,
            nie als Begruendung dafuer, dass etwas gedeckt ist.""";

    private static final Logger log = LoggerFactory.getLogger(ArztTool.class);

    private static final String WITHOUT_INPUT = "{\"type\":\"object\",\"properties\":{}}";

    private final AgentClient arztservice;
    private final ObjectMapper mapper;
    private final JsonNode fall;
    private final StatusChannel status;

    public ArztTool(AgentClient arztservice, ObjectMapper mapper, JsonNode fall,
                    StatusChannel status) {
        this.arztservice = Objects.requireNonNull(arztservice, "arztservice");
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
        this.fall = Objects.requireNonNull(fall, "fall");
        this.status = Objects.requireNonNull(status, "zwischenstand");
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(ToolSelection.ARZT)
                .description(DESCRIPTION)
                .inputSchema(WITHOUT_INPUT)
                .build();
    }

    @Override
    public String call(String input) {
        return call(input, null);
    }

    @Override
    public String call(String input, ToolContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("positionen", positionen());
        String behandlungsdatum = text(fall, "behandlungsdatum");
        if (behandlungsdatum != null) {
            data.put("behandlungsdatum", behandlungsdatum);
        }
        try {
            SubagentResponse answer =
                    arztservice.send(null, null, QUESTION, data, null, status);
            return info(answer);
        } catch (AgentUnreachableException ausfall) {
            log.warn("Arztservice nicht erreichbar; die Pruefung laeuft ohne Fachauskunft weiter",
                    ausfall);
            return error("Arztservice nicht erreichbar: " + ausfall.getMessage());
        }
    }

    private String info(SubagentResponse antwort) {
        if (antwort.outcome() != Outcome.COMPLETED || antwort.data().isEmpty()) {
            log.warn("Arztservice hat keine Auskunft geliefert (Ausgang {})", antwort.outcome());
            return error("Arztservice nicht erreichbar: keine verwertbare Auskunft (Ausgang "
                    + antwort.outcome() + ")");
        }
        return mapper.writeValueAsString(antwort.data());
    }

    private List<Map<String, Object>> positionen() {
        List<Map<String, Object>> positionen = new ArrayList<>();
        JsonNode list = fall.get("positionen");
        if (list == null || !list.isArray()) {
            return positionen;
        }
        for (JsonNode position : list) {
            Map<String, Object> entry = new LinkedHashMap<>();
            String gozNumber = text(position, "goz");
            if (gozNumber != null) {
                entry.put("gozNummer", gozNumber);
            }
            String label = text(position, "beschreibung");
            if (label != null) {
                entry.put("bezeichnung", label);
            }
            String zahn = text(position, "zahn");
            if (zahn != null) {
                entry.put("zahn", zahn);
            }
            JsonNode count = position.get("anzahl");
            if (count != null && count.isNumber()) {
                entry.put("anzahl", count.asInt());
            }
            String datum = text(position, "datum");
            if (datum != null) {
                entry.put("datum", datum);
            }
            positionen.add(entry);
        }
        return positionen;
    }

    private String error(String message) {
        return mapper.writeValueAsString(Map.of(ERROR, message));
    }

    private static String text(JsonNode elter, String field) {
        JsonNode value = elter == null ? null : elter.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.asString();
        } catch (RuntimeException keinText) {
            return null;
        }
    }
}
