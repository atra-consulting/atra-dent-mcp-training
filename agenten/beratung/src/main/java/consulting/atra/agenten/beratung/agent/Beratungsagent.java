package consulting.atra.agenten.beratung.agent;

import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.AgentResult;
import consulting.atra.agenten.a2a.agent.Agent;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.beratung.beleg.BelegCollector;
import consulting.atra.agenten.beratung.beleg.BelegFidelity;
import consulting.atra.agenten.beratung.beleg.ConfidentialityGuard;
import consulting.atra.agenten.beratung.tools.BeratungMessages;
import consulting.atra.agenten.beratung.tools.SignalTools;
import consulting.atra.agenten.beratung.tools.ToolSelection;
import consulting.atra.agenten.beratung.view.ViewCollector;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpContent;
import consulting.atra.agenten.mcp.ToolSource;
import consulting.atra.agenten.model.ModelUnreachableException;
import consulting.atra.agenten.model.ModelClient;
import consulting.atra.agenten.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Beratungsagent implements Agent {

    public static final String ARTIFACT = "beratung";

    public static final String SENDER = "beratung";

    private static final Logger log = LoggerFactory.getLogger(Beratungsagent.class);

    private static final String OPENING =
            "Womit darf ich anfangen? Ich berate zu den vier Tarifen von atra.dent, zu einem "
                    + "möglichen Wechsel und zu dem, was Ihr Tarif leistet.";

    private static final String MODELL_DISTURBED =
            "Ich kann Ihre Frage gerade nicht beantworten — mein Beratungsleitfaden ist "
                    + "vorübergehend nicht erreichbar. Bitte versuchen Sie es in ein paar Minuten "
                    + "noch einmal; bei dringenden Fragen hilft Ihnen die Sachbearbeitung weiter.";

    private static final String CONFIDENTIAL =
            "Ich kann Ihre Frage gerade nicht so beantworten, wie ich möchte. Bitte stellen Sie "
                    + "sie noch einmal — am besten mit Bezug auf eine konkrete Leistung, dann "
                    + "finde ich die passende Stelle in den Bedingungen.";

    private static final String STUCK =
            "Ich komme bei Ihrer Frage nicht weiter, ohne mich zu verzetteln. Stellen Sie sie "
                    + "gerne enger — etwa zu einem einzelnen Leistungsbereich oder einem "
                    + "einzelnen Tarif —, dann finde ich die passende Stelle in den Bedingungen.";

    private static final String THINKING = "sehe mir Ihre Frage an";

    private static final int ANSWERING = 1;

    private static final String ONCE_MORE = "formuliere die Antwort neu";

    private static final String STUCK_SHORT = "komme mit dem Nachschlagen nicht weiter";

    private static final String MODELL_DISTURBED_SHORT =
            "erreiche meinen Beratungsleitfaden gerade nicht";

    private static final String AMOUNT_ADOPTED = "uebernehme den Betrag von Ihrer Rechnung";

    private static final String AMOUNT_SUSPENDED = "sehe mir Ihre zweite Rechnung an";

    private final Set<String> openAssumptions = ConcurrentHashMap.newKeySet();

    private final Map<String, String> belegAmounts = new ConcurrentHashMap<>();

    private final ModelClient modellClient;
    private final String modell;
    private final ToolSource toolSource;
    private final History history;
    private final BelegCollector belegCollection;
    private final ConfidentialityGuard guard;
    private final ViewCollector viewCollector;
    private final ObjectMapper mapper;

    private final BelegFidelity belegFidelity;

    public Beratungsagent(ModelClient modellClient,
                          @Value("${spring.ai.google.genai.chat.model}") String modell,
                          ToolSource toolSource, History history,
                          BelegCollector belegsammlung, ConfidentialityGuard guard,
                          ViewCollector viewCollector, ObjectMapper mapper) {
        this.modellClient = Objects.requireNonNull(modellClient, "modellClient");
        this.modell = Objects.requireNonNull(modell, "modell");
        this.toolSource = Objects.requireNonNull(toolSource, "toolSource");
        this.history = Objects.requireNonNull(history, "gespraechsbuch");
        this.belegCollection = Objects.requireNonNull(belegsammlung, "belegsammlung");
        this.guard = Objects.requireNonNull(guard, "waechter");
        this.viewCollector = Objects.requireNonNull(viewCollector, "viewCollector");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.belegFidelity = new BelegFidelity(this.mapper);
    }

    @Override
    public AgentResult run(String contextId, String text,
                                      Map<String, Object> data, Long kundenId,
                                      StatusChannel status) {

        if (text.isBlank()) {
            return AgentResult.inputRequired(OPENING);
        }

        boolean angemeldet = kundenId != null;
        boolean belegImDataPart = data != null && data.get(Beratungsmodus.BELEG) != null;
        Beratungsmodus determined = Beratungsmodus.determine(angemeldet, data);
        if (determined == Beratungsmodus.BESTANDSBERATUNG && !belegImDataPart
                && openAssumptions.contains(contextId)) {
            determined = Beratungsmodus.RECHNUNG_EINREICHEN;
        }
        final Beratungsmodus mode = determined;
        boolean belegLiegtBei = belegImDataPart;
        ObservedTools observed = new ObservedTools(
                status, SENDER, ToolSelection.MAPPING, BeratungMessages.ALLE);
        history.record(contextId, ChatMessage.fromTheKunde(
                belegImDataPart ? text + belegBlock(data.get(Beratungsmodus.BELEG)) : text));

        SignalTools signals = new SignalTools();
        String belegAmount = belegAmount(contextId, data, belegImDataPart, status);
        List<ToolCallback> offered = new ArrayList<>(belegFidelity.wrap(
                toolSource.forPermission(ToolSelection.permission(mode)), belegAmount,
                (vomModell, ausDemBeleg) -> status.report(TracePoint.internal(
                        SENDER, "belegtreue", AMOUNT_ADOPTED,
                        "Gesamtbetrag aus der Extraktion",
                        amountEntries(vomModell, ausDemBeleg, observed.attempts())))));
        offered.addAll(signals.callbacks());
        List<ToolCallback> tools = observed.wrap(offered);
        String vertrag = mode.loggedIn() ? readOut(tools, kundenId) : null;

        status.report(TracePoint.model(SENDER, modell, "antworten", THINKING,
                "Antwort erarbeiten", offered(mode, tools, vertrag != null)));

        String antwort;
        try {
            antwort = MandantContext.with(kundenId, () -> modellClient.respond(
                    modell,
                    Beratungsprompt.forMode(mode, vertrag, belegLiegtBei),
                    history.history(contextId),
                    tools));
            status.report(TracePoint.model(SENDER, modell, "antworten", THINKING,
                    "Antwort erarbeiten", answered(antwort)));

        } catch (ObservedTools.ToolschleifeAusgeufertException ausgeufert) {
            status.report(TracePoint.model(SENDER, modell, "antworten", THINKING,
                    "Antwort erarbeiten", failed(ausgeufert)));
            Optional<SignalTools.Signal> anyway = signals.called();
            if (anyway.isPresent()) {
                return withOpenAssumption(contextId, mode,
                        ausSignal(contextId, anyway.get(), observed));
            }
            log.warn("Tool-Schleife abgebrochen im Gespraech {}", contextId, ausgeufert);
            status.report(TracePoint.internal(SENDER, "toolschleife", STUCK_SHORT,
                    "Tool-Schleife abgebrochen",
                    Map.of("calls", observed.attempts(),
                            "maxCount", ObservedTools.MAX_COUNT)));
            return withOpenAssumption(contextId, mode, AgentResult.failed(STUCK));

        } catch (ModelUnreachableException outage) {
            status.report(TracePoint.model(SENDER, modell, "antworten",
                    MODELL_DISTURBED_SHORT, "Modell nicht erreichbar",
                    unreachable(outage)));
            Optional<SignalTools.Signal> anyway = signals.called();
            if (anyway.isPresent()) {
                return withOpenAssumption(contextId, mode,
                        ausSignal(contextId, anyway.get(), observed));
            }
            log.error("Modell nicht erreichbar im Gespraech {}", contextId, outage);
            return withOpenAssumption(contextId, mode,
                    AgentResult.failed(MODELL_DISTURBED));
        }

        Optional<SignalTools.Signal> signal = signals.called();
        if (signal.isPresent()) {
            return withOpenAssumption(contextId, mode,
                    ausSignal(contextId, signal.get(), observed));
        }

        Optional<String> adopted = guard.violation(antwort, observed);
        if (adopted.isPresent()) {
            antwort = secondAttempt(contextId, mode, vertrag, observed,
                    adopted.get(), status);
            if (antwort == null) {
                return withOpenAssumption(contextId, mode,
                        AgentResult.failed(CONFIDENTIAL));
            }
        }

        history.record(contextId, ChatMessage.fromAgent(antwort));
        return withOpenAssumption(contextId, mode,
                AgentResult.completed(ARTIFACT, antwort, content(observed, status)));
    }

    @Override
    public void finish(String contextId) {
        openAssumptions.remove(contextId);
        belegAmounts.remove(contextId);
        history.forgotten(contextId);
    }

    private String belegAmount(String contextId, Map<String, Object> data,
                               boolean belegImDataPart, StatusChannel status) {
        if (!belegImDataPart) {
            return belegAmounts.get(contextId);
        }
        String fromBeleg = BelegFidelity.totalAmount(data.get(Beratungsmodus.BELEG));
        String sofar = belegAmounts.get(contextId);
        if (sofar != null && !sofar.equals(fromBeleg)) {
            belegAmounts.remove(contextId);
            status.report(TracePoint.internal(SENDER, "belegtreue", AMOUNT_SUSPENDED,
                    "Gesamtbetrag nicht uebernommen", suspended(fromBeleg, sofar)));
            return null;
        }
        if (fromBeleg != null) {
            belegAmounts.put(contextId, fromBeleg);
        }
        return fromBeleg;
    }

    private static Map<String, Object> failed(RuntimeException failure) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", ANSWERING);
        data.put("failed", TracePoint.truncate(String.valueOf(failure.getMessage())));
        return data;
    }

    private static Map<String, Object> unreachable(ModelUnreachableException failure) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", ANSWERING);
        data.put("reachable", false);
        data.put("failed", TracePoint.truncate(String.valueOf(failure.getMessage())));
        return data;
    }

    private static Map<String, Object> answered(String answer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", ANSWERING);
        data.put("result", TracePoint.truncate(answer));
        return data;
    }

    private static Map<String, Object> amountEntries(String vomModell, String ausDemBeleg,
                                                      int call) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", BelegFidelity.EINREICHEN);
        data.put("call", call);
        data.put("adopted", true);
        data.put("totalFromModel", vomModell);
        data.put("totalFromBeleg", ausDemBeleg);
        return data;
    }

    private static Map<String, Object> suspended(String ausDemBeleg, String sofar) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", BelegFidelity.EINREICHEN);
        data.put("adopted", false);
        data.put("reason", "zweite Rechnung im selben Gespraech");
        data.put("totalFromBeleg", ausDemBeleg);
        data.put("previousTotal", sofar);
        return data;
    }

    private AgentResult withOpenAssumption(String contextId, Beratungsmodus modus,
                                               AgentResult result) {
        if (result.outcome() == Outcome.INPUT_REQUIRED && modus == Beratungsmodus.RECHNUNG_EINREICHEN) {
            openAssumptions.add(contextId);
        } else {
            openAssumptions.remove(contextId);
            belegAmounts.remove(contextId);
        }
        return result;
    }

    private AgentResult ausSignal(String contextId, SignalTools.Signal signal,
                                       ObservedTools observed) {
        String signalText = signal.text();
        if (guard.violation(signalText, observed).isPresent()) {
            return AgentResult.failed(CONFIDENTIAL);
        }
        history.record(contextId, ChatMessage.fromAgent(signalText));
        return signal.art() == Outcome.INPUT_REQUIRED
                ? AgentResult.inputRequired(signalText)
                : AgentResult.rejected(signalText);
    }

    private static String readOut(List<ToolCallback> tools, Long kundenId) {
        Optional<ToolCallback> readVertrag = tools.stream()
                .filter(tool -> ToolSelection.VERTRAG_READ
                        .equals(tool.getToolDefinition().name()))
                .findFirst();
        if (readVertrag.isEmpty()) {
            return null;
        }
        try {
            return McpContent.unpack(
                    MandantContext.with(kundenId, () -> readVertrag.get().call("{}")));
        } catch (RuntimeException failure) {
            log.warn("Vertrag konnte nicht vorab gelesen werden; das Modell schlaegt selbst nach",
                    failure);
            return null;
        }
    }

    private String belegBlock(Object beleg) {
        try {
            return "\n\nBELEG (Extraktion aus dem Kernsystem, unveraendert):\n"
                    + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(beleg);
        } catch (RuntimeException cause) {
            log.warn("Beleg nicht serialisierbar", cause);
            return "\n\nBELEG: nicht lesbar.";
        }
    }

    private static Map<String, Object> offered(Beratungsmodus modus, List<ToolCallback> tools,
                                               boolean vertragVorgelesen) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", ANSWERING);
        data.put("loggedIn", modus.loggedIn());
        data.put("mode", modus.name());
        data.put("vertragReadOut", vertragVorgelesen);
        data.put("tools", tools.stream()
                .map(tool -> tool.getToolDefinition().name())
                .sorted()
                .toList());
        return data;
    }

    private String secondAttempt(String contextId, Beratungsmodus modus, String vertrag,
                                  ObservedTools observed, String uebernommen,
                                  StatusChannel status) {
        log.warn("VertraulichkeitsGuard hat angeschlagen im Gespraech {}: uebernommene "
                + "Wortfolge \"{}\"", contextId, uebernommen);
        status.report(TracePoint.internal(SENDER, "vertraulichkeitswaechter", ONCE_MORE,
                "Antwort nachgebessert",
                Map.of("adopted", TracePoint.truncate(uebernommen), "withoutTools", true)));

        try {
            String second = modellClient.respond(
                    modell,
                    Beratungsprompt.forMode(modus, vertrag, false) + "\n\n"
                            + Beratungsprompt.REFINED,
                    history.history(contextId),
                    List.of());

            if (guard.violation(second, observed).isPresent()) {
                log.error("Auch der zweite Versuch uebernimmt aus dem Beratungsleitfaden; "
                        + "es wird nichts ausgeliefert. Gespraech {}", contextId);
                return null;
            }
            return second;
        } catch (ModelUnreachableException failed) {
            log.error("Zweiter Versuch nicht moeglich im Gespraech {}", contextId, failed);
            return null;
        }
    }

    private Map<String, Object> content(ObservedTools observed, StatusChannel status) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("belege", belegCollection.collect(observed));
        content.put("hinweise", List.of());
        content.put("tools", observed.calls().stream()
                .map(ObservedTools.Toolaufruf::tool).toList());
        List<Map<String, Object>> views = viewCollector.collect(observed);
        viewCollector.trace(SENDER, views).ifPresent(status::report);
        content.put("views", views);
        return content;
    }
}
