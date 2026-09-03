package consulting.atra.agenten.schadensfall.check;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import consulting.atra.agenten.a2a.client.AgentClient;
import consulting.atra.agenten.a2a.client.AgentLookup;
import consulting.atra.agenten.a2a.client.AgentUnreachableException;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.Protocol;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.ToolSource;
import consulting.atra.agenten.model.ModelClient;
import consulting.atra.agenten.model.ModelUnreachableException;
import consulting.atra.agenten.model.ModelWithoutResponseException;
import consulting.atra.agenten.model.ChatMessage;
import consulting.atra.agenten.schadensfall.TestTools;
import consulting.atra.agenten.schadensfall.TestModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchadensfallCheckTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final Clock CLOCK = Clock.systemUTC();

    private static final String FALL = """
            {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-01-08",
             "positionen":[
               {"goz":"2197","betrag":"25.00","beschreibung":"Adhaesive Befestigung"},
               {"goz":"2150","betrag":"370.00","beschreibung":"Aufbaufuellung"}],
             "rechnungsbetrag":"395.00","status":"in_pruefung",
             "rechnung":{"patient":"Anna Mueller","rechnungsnummer":"2026-0042",
                         "gesamtbetrag":"395.00"},
             "eingereichtAm":"2026-01-10T09:00:00Z"}
            """;

    private static final String ERSTATTUNG_ARGUMENTS = """
            {"tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01",
             "behandlungsdatum":"2026-01-08",
             "positionen":[
               {"goz":"2197","leistungsbereich":"ZERH","betrag":"25.00","beschreibung":"Adhaesive Befestigung"},
               {"goz":"2150","leistungsbereich":"ZERH","betrag":"370.00","beschreibung":"Aufbaufuellung"}],
             "verbrauch":{"gesamt":"102.00"}}
            """;

    private static final String HANDOVER = """
            {"empfehlung":"freigabe","erstattungsvorschlag":"336.00",
             "positionen":[
               {"goz":"2197","leistungsbereich":"ZERH","zustand":"ENTHALTEN",
                "begruendung":"Zahnerhalt, 85 Prozent laut B.2.1"},
               {"goz":"2150","leistungsbereich":"ZERH","zustand":"ENTHALTEN",
                "begruendung":"Zahnerhalt, 85 Prozent laut B.2.1"}],
             "begruendung":"Beide Positionen sind dem Grunde nach gedeckt; der Vertrag ist aktiv."}
            """;

    private static final String ABGABE_OHNE_ZUORDNUNG = """
            {"empfehlung":"freigabe","erstattungsvorschlag":"336.00",
             "positionen":[
               {"goz":"2197","leistungsbereich":"ZERH","zustand":"ENTHALTEN",
                "begruendung":"Zahnerhalt, 85 Prozent laut B.2.1"},
               {"goz":"2150","zustand":"NICHT_BESTIMMBAR",
                "begruendung":"Ich kann die Nummer keiner Hauptbehandlung zuordnen"}],
             "begruendung":"Eine Position ist dem Grunde nach nicht gedeckt geklaert."}
            """;

    private TestTools.ToolLog store;
    private TestModel.Aufrufprotokoll modellLog;
    private Arztattrappe arzt;
    private ListAppender<ILoggingEvent> recording;

    @BeforeEach
    void cleanup() {
        store = new TestTools.ToolLog();
        modellLog = new TestModel.Aufrufprotokoll();
        arzt = new Arztattrappe();
        recording = new ListAppender<>();
        recording.start();
        checkLogger().addAppender(recording);
    }

    @AfterEach
    void hangUp() {
        checkLogger().detachAppender(recording);
    }

    private static Logger checkLogger() {
        return (Logger) LoggerFactory.getLogger(SchadensfallCheck.class);
    }

    @Test
    @DisplayName("a clean Schadensfall ends as a Freigabe and is written")
    void cleanFreigabeAndWritten() {
        CheckResult result = check(completeScript());

        assertThat(result.bewertung().freigabe()).isTrue();
        assertThat(result.bewertung().gruende()).isEmpty();
        assertThat(result.bewertung().erstattungsvorschlag())
                .isEqualByComparingTo(new BigDecimal("336.00"));
        assertThat(result.geschrieben()).isTrue();
        assertThat(result.hinweis()).isNull();
    }

    @Test
    @DisplayName("what is written is the guard's Bewertung together with the Protokoll")
    void argumentOfTheWritingTool() {
        check(completeScript());

        JsonNode arguments = IMAGES.readTree(store.argumentsOf(ToolSelection.BEWERTEN));
        assertThat(arguments.get("schadensfallId").asLong()).isEqualTo(50071L);
        JsonNode bewertung = arguments.get("bewertung");
        assertThat(bewertung.get("empfehlung").asString()).isEqualTo("freigabe");
        assertThat(bewertung.get("erstattungsvorschlag").asString()).isEqualTo("336.00");
        assertThat(bewertung.get("agent").asString()).isEqualTo("schadensfall");
        assertThat(bewertung.get("modell").asString()).isEqualTo(MODELL);
        assertThat(bewertung.get("positionen").size()).isEqualTo(2);
        assertThat(bewertung.get("arztauskunft").get("notwendigkeit").asString())
                .isEqualTo("ueblich");
        assertThat(arguments.get("protokoll").size()).isEqualTo(9);
        assertThat(resultTexts(arguments.get("protokoll"))).containsExactly(
                "Vertrag gelesen", "GOZ-Prüfung", "Tarife verglichen", "Verbrauch gelesen",
                "Erstattung berechnet", "Arztservice befragt", "Bedingungen nachgeschlagen",
                "Bewertung abgegeben", ProtokollCollector.GUARD);
    }

    @Test
    @DisplayName("every tool call carries the Kundennummer of the Schadensfall")
    void theMandantengrenzeOnTheSchadensfall() {
        check(completeScript());

        assertThat(store.kundenNumbers()).isNotEmpty().allMatch(number -> number == 4711L);
        assertThat(store.calls()).contains(ToolSelection.BEWERTEN);
    }

    @Test
    @DisplayName("the model is not offered schadensfall_bewerten")
    void theModelDoesNotSeeTheWritingTool() {
        check(completeScript());

        List<String> offered = modellLog.last().tools();
        assertThat(offered)
                .contains(ToolSelection.VERTRAG, ToolSelection.GOZ, ToolSelection.COMPARISON,
                        ToolSelection.ARZT, ToolSelection.SIGNAL)
                .doesNotContain(ToolSelection.BEWERTEN);
        assertThat(modellLog.last().systemprompt()).isEqualTo(CheckPrompt.SYSTEM);
        assertThat(modellLog.last().history()).contains("\"rechnungsbetrag\"");
    }

    @Test
    @DisplayName("an unresolved GOZ Nummer becomes a Eskalation, even if the model wants to approve")
    void anUnclearGozBecomesAEskalation() {
        store.responds(ToolSelection.GOZ, TestTools.GOZ_UNCLEAR);

        CheckResult result = check(completeScript());

        assertThat(result.bewertung().freigabe()).isFalse();
        assertThat(result.bewertung().codes()).containsExactly(Eskalationsgrund.GOZ_UNCLEAR);
        assertThat(result.bewertung().begruendung()).contains("gedeckt");
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("a NICHT_BESTIMMBAR Nummer without a mapping becomes a Eskalation")
    void nichtBestimmbarWithoutAMappingBecomesEskalation() {
        store.responds(ToolSelection.GOZ, TestTools.GOZ_NICHT_BESTIMMBAR);
        List<TestModel.Schritt> script = completeScript();
        script.set(script.size() - 1,
                new TestModel.Schritt(ToolSelection.SIGNAL, ABGABE_OHNE_ZUORDNUNG));

        CheckResult result = check(script);

        assertThat(result.bewertung().freigabe()).isFalse();
        assertThat(result.bewertung().codes()).contains(Eskalationsgrund.GOZ_UNCLEAR);
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("the same Nummer mapped to an ENTHALTEN Position is approved")
    void nichtBestimmbarWithAMappingBecomesFreigabe() {
        store.responds(ToolSelection.GOZ, TestTools.GOZ_NICHT_BESTIMMBAR);

        CheckResult result = check(completeScript());

        assertThat(result.bewertung().codes()).doesNotContain(Eskalationsgrund.GOZ_UNCLEAR);
    }

    @Test
    @DisplayName("a Rechenkern call with a foreign Tarif becomes a Eskalation, however cleanly it computed")
    void aForeignTarifInTheRechenkernBecomesAEskalation() {
        List<TestModel.Schritt> aForeignTarif = new ArrayList<>(completeScript());
        aForeignTarif.set(4, new TestModel.Schritt(ToolSelection.ERSTATTUNG,
                ERSTATTUNG_ARGUMENTS.replace("ATRA_DENT_B", "ATRA_DENT_PREMIUM")));

        CheckResult result = check(aForeignTarif);

        assertThat(result.bewertung().freigabe()).isFalse();
        assertThat(result.bewertung().codes()).containsExactly(Eskalationsgrund.BETRAG_DEVIATES);
        assertThat(result.bewertung().gruende().getFirst().text())
                .contains("ATRA_DENT_PREMIUM").contains("ATRA_DENT_B");
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("without bewertung_abgeben there is MODELL_WITHOUT_RESULT -- and still a Bewertung")
    void withoutASignalTheGuardReports() {
        List<TestModel.Schritt> withoutHandover = new ArrayList<>(completeScript());
        withoutHandover.removeLast();

        CheckResult result = check(withoutHandover);

        assertThat(result.bewertung().codes())
                .containsExactly(Eskalationsgrund.MODELL_WITHOUT_RESULT);
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("a failing Arztservice is a Eskalation and not an abort")
    void aFailingArztBecomesAEskalation() {
        arzt.failure = new AgentUnreachableException("Arztservice", "Kein Netz");

        CheckResult result = check(completeScript());

        assertThat(result.bewertung().codes())
                .containsExactly(Eskalationsgrund.ARZT_UNAVAILABLE);
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("no agent offering the skill means no tool, and that is a Eskalation too")
    void anUnwiredArztBecomesTheSameEskalation() {
        List<TestModel.Schritt> withoutArzt = new ArrayList<>(completeScript());
        withoutArzt.removeIf(schritt -> ToolSelection.ARZT.equals(schritt.tool()));

        CheckResult result = caseCheck(TestModel.script(modellLog, withoutArzt),
                TestTools.source(store), _ -> Optional.empty())
                .check(fall(), StatusChannel.discarded());

        assertThat(result.bewertung().codes())
                .containsExactly(Eskalationsgrund.ARZT_UNAVAILABLE);
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("a conflict while writing propagates -- the Schadensfall belongs to someone else")
    void theConflictPropagates() {
        store.disturbs(ToolSelection.BEWERTEN, new ToolExecutionException(
                bewertenDefinition(),
                new IllegalStateException("Error calling tool: [TextContent[text=Error invoking "
                        + "method: Eine Bewertung ist nur aus in_pruefung moeglich, der Fall "
                        + "steht auf genehmigt]]")));

        assertThatThrownBy(() -> check(completeScript()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("50071");
    }

    @Test
    @DisplayName("any other failure while writing leaves the Bewertung standing")
    void anotherFailureLeavesTheBewertungStanding() {
        store.disturbs(ToolSelection.BEWERTEN,
                new IllegalStateException("Error calling tool: connection refused"));

        CheckResult result = check(completeScript());

        assertThat(result.bewertung().freigabe()).isTrue();
        assertThat(result.geschrieben()).isFalse();
        assertThat(result.hinweis()).contains("nicht geschrieben");
    }

    @Test
    @DisplayName("if the MCP connection breaks before writing, the Bewertung stays")
    void aFailingToolLookupLeavesTheBewertungStanding() {
        ToolSource delegate = TestTools.source(store);
        int[] calls = {0};
        ToolSource aborting = allowed -> {
            if (++calls[0] > 1) {
                throw new IllegalStateException("MCP-Verbindung zum Kernsystem abgebrochen");
            }
            return delegate.forPermission(allowed);
        };

        CheckResult result =
                caseCheck(TestModel.script(modellLog, completeScript()), aborting)
                        .check(fall(), StatusChannel.discarded());

        assertThat(result.bewertung().freigabe()).isTrue();
        assertThat(result.geschrieben()).isFalse();
        assertThat(result.hinweis()).contains("nicht geschrieben").contains("abgebrochen");
    }

    @Test
    @DisplayName("if the model fails, the guard still checks")
    void aModelOutageEndsWithABewertung() {
        ModelClient failed = new ModelClient() {
            @Override
            public String classify(String modell, String systemprompt, String message) {
                throw new ModelUnreachableException("Anbieter meldet 503", null);
            }

            @Override
            public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                    List<ToolCallback> tools) {
                throw new ModelUnreachableException("Anbieter meldet 503", null);
            }
        };

        CheckResult result = caseCheck(failed).check(fall(), StatusChannel.discarded());

        assertThat(result.bewertung().codes())
                .contains(Eskalationsgrund.MODELL_WITHOUT_RESULT, Eskalationsgrund.VERTRAG_INACTIVE);
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("a runaway loop does not prevent the write")
    void aRunawayLoopStillWrites() {
        List<TestModel.Schritt> tooMany = new ArrayList<>();
        for (int i = 0; i <= ObservedTools.MAX_COUNT; i++) {
            tooMany.add(TestModel.Schritt.withoutArguments(ToolSelection.GOZ));
        }

        CheckResult result = check(tooMany);

        assertThat(result.bewertung().freigabe()).isFalse();
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("a wrapped loop exception reports the loop and not the provider")
    void aWrappedLoopExceptionIsDetected() {
        List<TracePoint> points = new ArrayList<>();
        ModelClient wrapping = new ModelClient() {
            @Override
            public String classify(String modell, String systemprompt, String message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                    List<ToolCallback> tools) {
                try {
                    for (int i = 0; i <= ObservedTools.MAX_COUNT; i++) {
                        tools.stream()
                                .filter(w -> w.getToolDefinition().name().equals(ToolSelection.GOZ))
                                .findFirst().orElseThrow().call("{}");
                    }
                    return "unerreichbar";
                } catch (RuntimeException ausDerSchleife) {
                    throw new ModelUnreachableException(modell, ausDerSchleife);
                }
            }
        };

        CheckResult result = caseCheck(wrapping).check(fall(), points::add);

        assertThat(points).extracting(TracePoint::operation).contains("toolschleife");
        assertThat(points).extracting(TracePoint::label).doesNotContain("Modell nicht erreichbar");
        TracePoint loop = points.stream()
                .filter(point -> "toolschleife".equals(point.operation())).findFirst()
                .orElseThrow();
        assertThat(loop.data())
                .containsEntry("maxCount", ObservedTools.MAX_COUNT);
        assertThat(result.geschrieben()).isTrue();
    }

    @Test
    @DisplayName("if the turn ends with the signal, the empty response is the success case")
    void aSignalFollowedBySilenceIsNoFailure() {
        List<TracePoint> points = new ArrayList<>();

        CheckResult result = caseCheck(signalThenSilence()).check(fall(), points::add);

        assertThat(result.bewertung().freigabe()).isTrue();
        assertThat(result.bewertung().codes()).doesNotContain(Eskalationsgrund.MODELL_WITHOUT_RESULT);
        assertThat(result.geschrieben()).isTrue();
        assertThat(points).extracting(TracePoint::label).doesNotContain("Modell nicht erreichbar");
        TracePoint closing = points.stream()
                .filter(point -> "Zug mit Signal beendet".equals(point.label())).findFirst()
                .orElseThrow(() -> new AssertionError("Kein Punkt zum Abschluss des Zuges in "
                        + points.stream().map(TracePoint::label).toList()));
        assertThat(closing.data()).containsEntry("signal", ToolSelection.SIGNAL);
        assertThat(errorMessages()).isEmpty();
    }

    @Test
    @DisplayName("the model check closes with a result of its own")
    void theModelCheckCloses() {
        List<TracePoint> points = new ArrayList<>();

        caseCheck(TestModel.script(modellLog, completeScript())).check(fall(), points::add);

        List<TracePoint> viaModell = points.stream()
                .filter(point -> point.protocol() == Protocol.MODELL).toList();
        assertThat(viaModell).hasSize(2);
        assertThat(viaModell.getFirst().data())
                .containsEntry("call", 1)
                .doesNotContainKey("result");
        assertThat(viaModell.getLast().data())
                .containsEntry("call", 1)
                .containsKey("result");
    }

    @Test
    @DisplayName("first the Vorschlag, then the weighing -- that is what the status line says too")
    void statusLinesAppearInTheOrderOfTheWork() {
        List<TracePoint> points = new ArrayList<>();

        caseCheck(signalThenSilence()).check(fall(), points::add);

        List<String> texts = points.stream()
                .filter(point -> "pruefen".equals(point.operation())
                        || "freigabewaechter".equals(point.operation()))
                .map(TracePoint::text)
                .toList();

        assertThat(texts).containsSubsequence(
                "habe meinen Vorschlag abgegeben",
                "wäge den Vorschlag gegen die Akte ab");
    }

    @Test
    @DisplayName("an empty response without a signal is a Eskalation -- but no provider failure")
    void anEmptyResponseWithoutASignalIsNoProviderFailure() {
        List<TracePoint> points = new ArrayList<>();
        ModelClient silent = new ModelClient() {
            @Override
            public String classify(String modell, String systemprompt, String message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                    List<ToolCallback> tools) {
                throw new ModelWithoutResponseException(modell);
            }
        };

        CheckResult result = caseCheck(silent).check(fall(), points::add);

        assertThat(result.bewertung().codes()).contains(Eskalationsgrund.MODELL_WITHOUT_RESULT);
        assertThat(result.geschrieben()).isTrue();
        assertThat(points).extracting(TracePoint::label)
                .doesNotContain("Modell nicht erreichbar")
                .contains("Modell ohne Bewertung");
        TracePoint withoutABewertung = points.stream()
                .filter(point -> "Modell ohne Bewertung".equals(point.label())).findFirst()
                .orElseThrow();
        assertThat(withoutABewertung.data())
                .containsEntry("reachable", true)
                .containsEntry("signal", false);
    }

    @Test
    @DisplayName("a provider failure stays a provider failure")
    void aModelFailureStaysUnreachable() {
        List<TracePoint> points = new ArrayList<>();
        ModelClient failed = new ModelClient() {
            @Override
            public String classify(String modell, String systemprompt, String message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                    List<ToolCallback> tools) {
                throw new ModelUnreachableException(modell,
                        new IllegalStateException("503 vom Anbieter"));
            }
        };

        CheckResult result = caseCheck(failed).check(fall(), points::add);

        assertThat(result.bewertung().codes()).contains(Eskalationsgrund.MODELL_WITHOUT_RESULT);
        assertThat(points).extracting(TracePoint::label).contains("Modell nicht erreichbar");
    }

    @Test
    @DisplayName("the trace shows the model call, the tools and the guard's decision")
    void theTracePointsAreThere() {
        List<TracePoint> points = new ArrayList<>();

        caseCheck(TestModel.script(modellLog, completeScript()))
                .check(fall(), points::add);

        assertThat(points).extracting(TracePoint::operation)
                .contains("pruefen", ToolSelection.VERTRAG, ToolSelection.ARZT, "freigabewaechter",
                        ToolSelection.BEWERTEN);
        TracePoint guard = points.stream()
                .filter(point -> "freigabewaechter".equals(point.operation())).findFirst()
                .orElseThrow();
        assertThat(guard.data()).containsEntry("empfehlung", "freigabe");
    }

    @Test
    @DisplayName("a Schadensfall without a Kundennummer is not checked at all")
    void withoutAKundennummerNoCheck() {
        JsonNode withoutKunde = IMAGES.readTree("{\"id\":50071}");

        assertThatThrownBy(() -> caseCheck(TestModel.script(modellLog, List.of()))
                .check(withoutKunde, StatusChannel.discarded()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kundenId");
    }


    private static final String MODELL = "gemini-test";

    private ModelClient signalThenSilence() {
        ModelClient script = TestModel.script(modellLog, completeScript());
        return new ModelClient() {
            @Override
            public String classify(String modell, String systemprompt, String message) {
                return script.classify(modell, systemprompt, message);
            }

            @Override
            public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                    List<ToolCallback> tools) {
                script.respond(modell, systemprompt, history, tools);
                throw new ModelWithoutResponseException(modell);
            }
        };
    }

    private List<String> errorMessages() {
        return recording.list.stream()
                .filter(entry -> entry.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private CheckResult check(List<TestModel.Schritt> skript) {
        return caseCheck(TestModel.script(modellLog, skript))
                .check(fall(), StatusChannel.discarded());
    }

    private SchadensfallCheck caseCheck(ModelClient modellClient) {
        return caseCheck(modellClient, TestTools.source(store));
    }

    private SchadensfallCheck caseCheck(ModelClient modellClient, ToolSource toolSource) {
        return caseCheck(modellClient, toolSource, _ -> Optional.of(arzt));
    }

    private SchadensfallCheck caseCheck(ModelClient modellClient, ToolSource toolSource,
                                        AgentLookup subagents) {
        return new SchadensfallCheck(modellClient, MODELL, toolSource, subagents,
                new ApprovalGuard(new BigDecimal("1500.00")), IMAGES, CLOCK);
    }

    private static List<TestModel.Schritt> completeScript() {
        return new ArrayList<>(List.of(
                TestModel.Schritt.withoutArguments(ToolSelection.VERTRAG),
                new TestModel.Schritt(ToolSelection.GOZ,
                        "{\"tarif\":\"ATRA_DENT_B\",\"nummern\":[\"2197\",\"2150\"]}"),
                TestModel.Schritt.withoutArguments(ToolSelection.COMPARISON),
                TestModel.Schritt.withoutArguments(ToolSelection.SCHADENSFAELLE),
                new TestModel.Schritt(ToolSelection.ERSTATTUNG, ERSTATTUNG_ARGUMENTS),
                TestModel.Schritt.withoutArguments(ToolSelection.ARZT),
                new TestModel.Schritt(ToolSelection.BEDINGUNGEN, "{\"frage\":\"Zahnerhalt\"}"),
                new TestModel.Schritt(ToolSelection.SIGNAL, HANDOVER)));
    }

    private static JsonNode fall() {
        return IMAGES.readTree(FALL);
    }

    private static List<String> resultTexts(JsonNode protokoll) {
        List<String> steps = new ArrayList<>();
        protokoll.forEach(entry -> steps.add(entry.get("schritt").asString()));
        return steps;
    }

    private static org.springframework.ai.tool.definition.ToolDefinition bewertenDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(ToolSelection.BEWERTEN).description("Test")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
    }

    private static final class Arztattrappe implements AgentClient {

        private RuntimeException failure;

        @Override
        public SubagentResponse send(String contextId, String taskId, String text,
                                       Map<String, Object> data, Long kundenId,
                                       StatusChannel status) {
            if (failure != null) {
                throw failure;
            }
            return new SubagentResponse(Outcome.COMPLETED, "Die Positionen passen zueinander.",
                    "k1", "t1", Map.of(
                            "plausibilitaet", "plausibel",
                            "notwendigkeit", "ueblich",
                            "text", "Adhaesive Befestigung und Aufbaufuellung passen zusammen.",
                            "hinweis", "Sprachmodell-Auskunft, kein Beleg.",
                            "quelle", "sprachmodell"));
        }
    }
}
