package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpContent;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KundendatenTool {

    public static final String TOOL = "kundendaten_nachschlagen";

    public static final String SOURCE = "mein_vertrag_lesen";

    static final String HINT = "hinweis";

    static final String ERROR = "fehler";

    static final String NOBODY = "Zu dieser Unterhaltung ist niemand angemeldet; es gibt "
            + "keine Akte, in der etwas nachzuschlagen waere. Stelle die Frage der Kundin "
            + "und erfinde nichts.";

    static final String UNREADABLE = "Die Akte ist gerade nicht lesbar. Stelle die Frage der "
            + "Kundin und erfinde nichts.";

    private static final List<Projected> PROJECTION = List.of(
            new Projected("vorname", List.of("vorname")),
            new Projected("nachname", List.of("nachname")),
            new Projected("geburtsdatum", List.of("geburtsdatum")),
            new Projected("tarif", List.of("tarifId", "tarif")),
            new Projected("versicherungsbeginn", List.of("versicherungsbeginn")),
            new Projected("status", List.of("status")));

    private static final Logger log = LoggerFactory.getLogger(KundendatenTool.class);

    private final ToolSource tools;
    private final ObjectMapper mapper;

    private ToolCallback delegate;

    public KundendatenTool(ToolSource tools) {
        this(tools, JsonMapper.builder().build());
    }

    public KundendatenTool(ToolSource tools, ObjectMapper mapper) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Tool(name = TOOL, description = """
        Schlaegt in der Akte der angemeldeten Kundin nach, was atra.dent
        ueber sie weiss: vorname, nachname, geburtsdatum, tarif,
        versicherungsbeginn und status. Mehr liefert dieses Tool nicht --
        Anschrift, E-Mail, Telefonnummer und Kundennummer bekommst du hier
        nicht und gibst du auch nie weiter.

        Ruf es, wenn ein Agent nach einer dieser Angaben zurueckfragt,
        statt die Frage an die Kundin weiterzureichen. Sie ist angemeldet
        und weiss nicht, warum du sie nach ihrem eigenen Tarif fragst.

        Das Tool braucht keine Parameter; wer angemeldet ist, ist technisch
        geregelt. Ist niemand angemeldet, sagt es das -- dann fragst du die
        Kundin.""")
    public String readKundendaten() {
        TaskContext.Auftrag task = TaskContext.current();
        Long kundenId = task.kundenId();
        String answer = kundenId == null ? message(HINT, NOBODY) : lookup(kundenId);
        report(task, kundenId, answer);
        return answer;
    }

    private String lookup(Long kundenId) {
        ToolCallback callback = source();
        if (callback == null) {
            log.warn("Das Kernsystem bietet {} nicht an; es gibt nichts nachzuschlagen", SOURCE);
            return message(ERROR, UNREADABLE);
        }
        try {
            String raw = MandantContext.with(kundenId, () -> callback.call("{}"));
            Map<String, Object> projected = project(McpContent.unpack(raw));
            return projected.isEmpty()
                    ? message(ERROR, UNREADABLE)
                    : mapper.writeValueAsString(projected);
        } catch (RuntimeException failure) {
            log.warn("Die Akte war nicht lesbar; die Frage geht an die Kundin zurueck", failure);
            return message(ERROR, UNREADABLE);
        }
    }

    private Map<String, Object> project(String akte) {
        Map<String, Object> projected = new LinkedHashMap<>();
        if (akte == null || akte.isBlank()) {
            return projected;
        }
        JsonNode root;
        try {
            root = mapper.readTree(akte);
        } catch (RuntimeException unreadable) {
            log.warn("Die Antwort des Kernsystems war kein JSON", unreadable);
            return projected;
        }
        if (root == null || !root.isObject()) {
            return projected;
        }
        for (Projected field : PROJECTION) {
            String value = firstOf(root, field.sources());
            if (value != null) {
                projected.put(field.name(), value);
            }
        }
        return projected;
    }

    private void report(TaskContext.Auftrag task, Long kundenId, String answer) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (kundenId != null) {
            data.put("header x-kunden-id", kundenId);
        }
        data.put("tool", SOURCE);
        data.put("fields", PROJECTION.stream().map(Projected::name).toList());
        data.put("result", TracePoint.truncate(answer));
        task.status().report(TracePoint.internal(Orchestrator.SENDER, TOOL,
                "sehe in Ihrer Akte nach", "Kundendaten nachschlagen", data));
    }

    private synchronized ToolCallback source() {
        if (delegate == null) {
            delegate = tools.forPermission((tool, server) ->
                            SOURCE.equals(tool) && McpToolSource.KERNSYSTEM.equals(server))
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
        return delegate;
    }

    private String message(String key, String text) {
        return mapper.writeValueAsString(Map.of(key, text));
    }

    private static String firstOf(JsonNode akte, List<String> names) {
        for (String name : names) {
            JsonNode value = akte.get(name);
            if (value == null || value.isNull() || !value.isValueNode()) {
                continue;
            }
            String text = value.asString();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private record Projected(String name, List<String> sources) {
    }
}
