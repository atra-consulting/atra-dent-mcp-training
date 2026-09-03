package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.a2a.client.AgentLookup;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.ToolSource;
import consulting.atra.agenten.model.ModelClient;
import consulting.atra.agenten.model.ModelUnreachableException;
import consulting.atra.agenten.model.ModelWithoutResponseException;
import consulting.atra.agenten.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class SchadensfallCheck {

    public static final String SENDER = "schadensfall";

    private static final Logger log = LoggerFactory.getLogger(SchadensfallCheck.class);

    private static final String THINKING = "sehe mir den Fall an";

    private static final int CHECKING = 1;

    private static final String MODELL_DISTURBED_SHORT = "erreiche mein Sprachmodell gerade nicht";

    private static final String STUCK_SHORT = "komme mit dem Nachschlagen nicht weiter";

    private static final String WITHOUT_BEWERTUNG_SHORT = "bekomme keine Bewertung aus dem Sprachmodell";

    private static final String HANDED_OVER_SHORT = "habe meinen Vorschlag abgegeben";

    private static final String DECIDED = "wäge den Vorschlag gegen die Akte ab";

    private final ModelClient modellClient;
    private final String modell;
    private final ToolSource toolSource;
    private final AgentLookup subagents;
    private final ApprovalGuard guard;
    private final BewertungWriter writer;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SchadensfallCheck(ModelClient modellClient, String modell,
                        ToolSource toolSource, AgentLookup subagents,
                        ApprovalGuard guard, ObjectMapper mapper, Clock clock) {
        this.modellClient = Objects.requireNonNull(modellClient, "modellClient");
        this.modell = Objects.requireNonNull(modell, "modell");
        this.toolSource = Objects.requireNonNull(toolSource, "toolSource");
        this.subagents = Objects.requireNonNull(subagents, "subagents");
        this.guard = Objects.requireNonNull(guard, "waechter");
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
        this.clock = Objects.requireNonNull(clock, "uhr");
        this.writer = new BewertungWriter(this.mapper);
    }

    public CheckResult check(JsonNode fall, StatusChannel status) {
        Objects.requireNonNull(fall, "fall");
        Objects.requireNonNull(status, "zwischenstand");
        long fallId = number(fall, "id");
        long kundenId = number(fall, "kundenId");

        ObservedTools observed = new ObservedTools(status, SENDER,
                SchadensfallMapping.ALLE, SchadensfallMessages.ALLE);
        Bewertungssignal signal = new Bewertungssignal();
        List<ToolCallback> offered = new ArrayList<>(toolSource.forPermission(ToolSelection.forModel()));
        subagents.withSkill(ArztTool.SKILL)
                .ifPresent(agent -> offered.add(new ArztTool(agent, mapper, fall, status)));
        offered.addAll(signal.callbacks());
        List<ToolCallback> tools = observed.wrap(offered);

        status.report(TracePoint.model(SENDER, modell, "pruefen", THINKING,
                "Fall pruefen", offered(fallId, kundenId, tools)));

        modellRun(fall, kundenId, modell, tools, status, observed, signal);

        RawFindings findings = RawFindings.aus(observed, mapper);
        BewertungResult result =
                guard.check(fall, signal.handedOver().orElse(null), findings);
        status.report(TracePoint.internal(SENDER, "freigabewaechter", DECIDED,
                "Freigabewächter", Map.of(
                        "empfehlung", result.empfehlung(),
                        "gruende", result.codes())));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, result, mapper, clock);
        return write(fallId, kundenId, result, positionenIm(fall), protokoll, modell,
                status);
    }

    private static int positionenIm(JsonNode fall) {
        JsonNode positionen = fall == null ? null : fall.get("positionen");
        return positionen == null || !positionen.isArray() ? 0 : positionen.size();
    }


    private void modellRun(JsonNode fall, long kundenId, String modell, List<ToolCallback> tools,
                            StatusChannel status, ObservedTools observed,
                            Bewertungssignal signal) {
        try {
            String answer = MandantContext.with(kundenId, () -> modellClient.respond(modell,
                    CheckPrompt.SYSTEM,
                    List.of(ChatMessage.fromTheKunde(CheckPrompt.userText(fall))),
                    tools));
            status.report(TracePoint.model(SENDER, modell, "pruefen", THINKING,
                    "Fall pruefen", answered(answer)));

        } catch (ObservedTools.ToolschleifeAusgeufertException ausgeufert) {
            status.report(TracePoint.model(SENDER, modell, "pruefen", THINKING,
                    "Fall pruefen", failed(ausgeufert)));
            stuck(status, observed, ausgeufert);

        } catch (ModelWithoutResponseException ohneAntwort) {
            if (signal.handedOver().isEmpty()) {
                withoutABewertung(status, modell, ohneAntwort);
                return;
            }
            Map<String, Object> handedOver = new LinkedHashMap<>();
            handedOver.put("call", CHECKING);
            handedOver.put("signal", ToolSelection.SIGNAL);
            handedOver.put("result", ToolSelection.SIGNAL);
            status.report(TracePoint.model(SENDER, modell, "pruefen", HANDED_OVER_SHORT,
                    "Zug mit Signal beendet", handedOver));

        } catch (ModelUnreachableException failed) {
            if (failed.getCause() instanceof ObservedTools.ToolschleifeAusgeufertException
                    ausgeufert) {
                status.report(TracePoint.model(SENDER, modell, "pruefen", THINKING,
                        "Fall pruefen", failed(ausgeufert)));
                stuck(status, observed, ausgeufert);
                return;
            }
            unreachable(status, modell, failed);
        }
    }

    private static Map<String, Object> answered(String answer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", CHECKING);
        data.put("result", TracePoint.truncate(String.valueOf(answer)));
        return data;
    }

    private static Map<String, Object> failed(RuntimeException failure) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", CHECKING);
        data.put("failed", TracePoint.truncate(String.valueOf(failure.getMessage())));
        return data;
    }

    private static void unreachable(StatusChannel status, String modell,
                                        ModelUnreachableException failed) {
        log.error("Modell bei der Pruefung ohne verwertbares Ergebnis", failed);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", CHECKING);
        data.put("reachable", false);
        data.put("failed", TracePoint.truncate(String.valueOf(failed.getMessage())));
        status.report(TracePoint.model(SENDER, modell, "pruefen",
                MODELL_DISTURBED_SHORT, "Modell nicht erreichbar", data));
    }

    private static void withoutABewertung(StatusChannel status, String modell,
                                      ModelWithoutResponseException ohneAntwort) {
        log.warn("Modell bei der Pruefung ohne Bewertung", ohneAntwort);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", CHECKING);
        data.put("reachable", true);
        data.put("signal", false);
        data.put("failed", TracePoint.truncate(String.valueOf(ohneAntwort.getMessage())));
        status.report(TracePoint.model(SENDER, modell, "pruefen",
                WITHOUT_BEWERTUNG_SHORT, "Modell ohne Bewertung", data));
    }

    private static void stuck(StatusChannel status, ObservedTools observed,
                                 RuntimeException ausgeufert) {
        log.warn("Tool-Schleife bei der Pruefung abgebrochen", ausgeufert);
        status.report(TracePoint.internal(SENDER, "toolschleife", STUCK_SHORT,
                "Tool-Schleife abgebrochen",
                Map.of("calls", observed.attempts(),
                        "maxCount", ObservedTools.MAX_COUNT)));
    }

    private CheckResult write(long fallId, long kundenId, BewertungResult result,
                                    int positionenImFall, List<Map<String, Object>> protokoll,
                                    String modell, StatusChannel status) {
        ObservedTools writeWrapper = new ObservedTools(status, SENDER,
                SchadensfallMapping.ALLE, SchadensfallMessages.ALLE);
        try {
            Optional<ToolCallback> bewerten =
                    writeWrapper.wrap(toolSource.forPermission(ToolSelection.forCode())).stream()
                            .filter(tool ->
                                    ToolSelection.BEWERTEN.equals(tool.getToolDefinition().name()))
                            .findFirst();
            if (bewerten.isEmpty()) {
                log.error("Kein {} verfuegbar; Fall {} bleibt ungeschrieben",
                        ToolSelection.BEWERTEN, fallId);
                return new CheckResult(result, protokoll, false,
                        "Die Bewertung konnte nicht geschrieben werden: Das Kernsystem bietet "
                                + ToolSelection.BEWERTEN + " nicht an.");
            }
            writer.write(fallId, kundenId, result, positionenImFall, protokoll, modell,
                    bewerten.get());
            return new CheckResult(result, protokoll, true, null);
        } catch (ConflictException conflict) {
            throw conflict;
        } catch (RuntimeException failure) {
            log.error("Bewertung zu Fall {} konnte nicht geschrieben werden", fallId, failure);
            return new CheckResult(result, protokoll, false,
                    "Die Bewertung konnte nicht geschrieben werden: " + failure.getMessage());
        }
    }

    private static Map<String, Object> offered(long fallId, long kundenId,
                                               List<ToolCallback> tools) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", CHECKING);
        data.put("schadensfallId", fallId);
        data.put("kundenId", kundenId);
        data.put("tools", tools.stream()
                .map(tool -> tool.getToolDefinition().name())
                .sorted()
                .toList());
        return data;
    }

    private static long number(JsonNode fall, String field) {
        JsonNode value = fall.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(
                    "Der Fall hat kein auswertbares Feld '" + field + "'; so laesst er sich nicht "
                            + "pruefen");
        }
        return value.asLong();
    }
}
