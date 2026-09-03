package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.client.AgentUnreachableException;
import consulting.atra.agenten.a2a.client.AgentClient;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import consulting.atra.agenten.a2a.agent.TracePoint;
import io.a2a.spec.AgentCard;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class AgentTool {

    static final String VIEWS = "views";

    static final String KIND = "kind";

    public static final String TOOL = "nachricht_an_agenten";

    public static final int MAX_COUNT = 6;

    private final SubagentCatalog catalog;
    private final ThreadRegistry threadRegistry;
    private final Function<AgentCard, AgentClient> accessFactory;

    public AgentTool(SubagentCatalog catalog, ThreadRegistry threadRegistry,
                           Function<AgentCard, AgentClient> zugangFabrik) {
        this.catalog = Objects.requireNonNull(catalog, "katalog");
        this.threadRegistry = Objects.requireNonNull(threadRegistry, "threadRegistry");
        this.accessFactory = Objects.requireNonNull(zugangFabrik, "zugangFabrik");
    }

    @Tool(name = TOOL, description = """
        Reicht ein Anliegen an einen Fachagenten weiter und liefert dessen
        Antwort. agent muss exakt einer der Namen aus dem Abschnitt
        VERFUEGBARE AGENTEN sein. Formuliere das Anliegen eigenstaendig
        verstaendlich, aus dem, was im Gespraech schon gefallen ist, und rufe
        dies SOFORT: Fehlende Angaben erfragt der Agent selbst.""")
    public String messageToAgent(
            @ToolParam(description = "Name des Agenten") String agent,
            @ToolParam(description = "das vollstaendige Anliegen") String anliegen) {
        AgentCard card = catalog.card(agent);
        String note = card == null ? agent + " (unbekannt)" : agent;
        TaskContext.Auftrag task = TaskContext.current();
        Routings sofar = task.weiterleitungen();
        if (sofar.count() >= MAX_COUNT) {
            task.status().report(TracePoint.internal(Orchestrator.SENDER, "umleitungsgrenze",
                    "komme mit dem Weiterreichen nicht weiter", "Umleitungsgrenze erreicht",
                    Map.of("calls", sofar.count(), "maxCount", MAX_COUNT)));
            return "Du hast in diesem Zug genug Agenten gefragt. Antworte der Kundin mit dem,"
                    + " was du hast, und erfinde keine Fachauskunft.";
        }
        if (sofar.alreadyAsked(note, anliegen)) {
            task.status().report(TracePoint.internal(Orchestrator.SENDER, "wiederholte-frage",
                    "frage nicht zweimal dasselbe", "Wiederholte Frage abgewiesen",
                    Map.of("agent", agent)));
            return "Diesen Agenten hast du mit genau dieser Frage schon gefragt. Frage einen"
                    + " anderen, formuliere die Frage anders - oder antworte der Kundin.";
        }
        sofar.add(note, anliegen);
        if (card == null) {
            return "Unbekannter Agent '" + agent + "'. Verfuegbar: " + catalog.names();
        }
        ThreadRegistry.Thread thread = threadRegistry.thread(task.contextId(), agent);
        try {
            SubagentResponse response = accessFactory.apply(card).send(
                    thread == null ? null : thread.contextId(),
                    thread == null ? null : thread.taskId(),
                    anliegen, task.data(), task.kundenId(), task.status()::report);
            threadRegistry.record(task.contextId(), agent, response);
            return switch (response.outcome()) {
                case INPUT_REQUIRED -> "[Rueckfrage von " + agent + " - "
                        + followUp(task.kundenId()) + "]: " + response.text();
                case COMPLETED -> {
                    task.auskuenfte().adopt(agent, response.data());
                    yield "[Antwort von " + agent + kind(response) + " - gib sie in eigener Stimme"
                        + " wieder, Belege und Zahlen unveraendert]: " + response.text()
                        + renderings(response);
                }
                case REJECTED -> "[Absage von " + agent + " - lies die Begruendung: Haelt er"
                        + " sich fuer nicht zustaendig, darfst du es bei einem anderen Agenten"
                        + " oder mit einer anderen Frage erneut versuchen. Sagt er, dass er es"
                        + " nicht tun darf, gib die Absage wieder]: " + response.text();
                case FAILED -> "Der Agent '" + agent + "' konnte den Vorgang nicht zu Ende"
                        + " bringen. Sage der Kundin ehrlich, dass die Fachauskunft derzeit"
                        + " nicht verfuegbar ist; erfinde keine Antwort.";
            };
        } catch (AgentUnreachableException _) {
            return "Der Agent '" + agent + "' ist gerade nicht erreichbar."
                    + " Sage der Kundin ehrlich, dass die Fachauskunft derzeit"
                    + " nicht verfuegbar ist; erfinde keine Antwort.";
        }
    }

    private static String kind(SubagentResponse response) {
        String name = response.artifactName();
        if (name == null || name.isBlank()) {
            return "";
        }
        return ", Art der Antwort: " + name;
    }

    static String followUp(Long kundenId) {
        if (kundenId == null) {
            return "stelle sie der Kundin woertlich und beantworte sie nicht selbst";
        }
        return "fragt er nach Name, Vorname, Nachname, Geburtsdatum, Tarif oder"
                + " Versicherungsbeginn, dann steht das in der Akte: rufe"
                + " kundendaten_nachschlagen und beantworte es ihm selbst, ohne die Frage"
                + " der Kundin zu zeigen. Fragt er nach etwas anderem, stelle sie der"
                + " Kundin woertlich und beantworte sie nicht selbst";
    }

    private static String renderings(SubagentResponse response) {
        if (!(response.data().get(VIEWS) instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        List<String> kinds = list.stream()
                .filter(Map.class::isInstance)
                .map(view -> ((Map<?, ?>) view).get(KIND))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
        if (kinds.isEmpty()) {
            return "";
        }
        return " [Der Kundin wird unter deiner Antwort dies angezeigt: "
                + String.join(", ", kinds)
                + ". Fuehre in einem Satz darauf hin und zaehle die Zahlen NICHT auf.]";
    }
}
