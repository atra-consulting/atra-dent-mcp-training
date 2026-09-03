package consulting.atra.agenten.beratung.agent;

import consulting.atra.agenten.a2a.agent.*;
import consulting.atra.agenten.beratung.TestModel;
import consulting.atra.agenten.beratung.TestTools;
import consulting.atra.agenten.beratung.beleg.BelegCollector;
import consulting.atra.agenten.beratung.beleg.BelegFidelity;
import consulting.atra.agenten.beratung.beleg.ConfidentialityGuard;
import consulting.atra.agenten.beratung.tools.SignalTools;
import consulting.atra.agenten.beratung.tools.ToolSelection;
import consulting.atra.agenten.beratung.view.ViewCollector;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpWrapper;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.NestedTestConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BeratungsagentTest {

    private static final String MODELLE = "flash";

    private static final BelegCollector BELEGE =
            new BelegCollector(tools.jackson.databind.json.JsonMapper.builder().build());

    private static final ConfidentialityGuard GUARD =
            new ConfidentialityGuard(tools.jackson.databind.json.JsonMapper.builder().build());

    private static final ViewCollector VIEWS =
            new ViewCollector(tools.jackson.databind.json.JsonMapper.builder().build());

    private static final tools.jackson.databind.ObjectMapper MAPPER =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private static final Map<String, Object> BELEG = Map.of(Beratungsmodus.BELEG, Map.of(
            "rechnungsnummer", "KKW-2026-003288",
            "gesamtbetrag", "1746.28",
            "positionen", List.of(
                    Map.of("ziffer", "2210", "leistung", "Vollkrone", "betrag", "773.68"),
                    Map.of("leistung", "Zwei vollkeramische Kronen", "betrag", "972.60"))));

    private History store;

    private List<TracePoint> reported;

    @BeforeEach
    void prepare() {
        store = new History();
        reported = new ArrayList<>();
    }

    private List<String> texts() {
        return reported.stream().map(TracePoint::text).toList();
    }

    private List<TracePoint> withProtokoll(Protocol protokoll) {
        return reported.stream().filter(point -> point.protocol() == protokoll).toList();
    }

    @Test
    @DisplayName("an empty message yields an opening without a model call")
    void anEmptyMessageWithoutTheModel() {
        List<String> modellCalls = new ArrayList<>();
        Beratungsagent agent = agent(prompt -> {
            modellCalls.add(prompt);
            return "sollte nicht aufgerufen werden";
        });

        AgentResult result = agent.run("g1", "", Map.of(), 10001L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(modellCalls).isEmpty();
    }

    @Test
    @DisplayName("the system prompt depends on the login state")
    void theSystemPromptDependsOnTheLoginState() {
        List<String> prompts = new ArrayList<>();
        Beratungsagent withRecord = agent(prompt -> {
            prompts.add(prompt);
            return "Antwort";
        });
        withRecord.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        Beratungsagent withoutRecord = agent(prompt -> {
            prompts.add(prompt);
            return "Antwort";
        });
        withoutRecord.run("g2", "Welche Tarife gibt es?", Map.of(), null, reported::add);

        assertThat(prompts.get(0)).contains("DIESE KUNDIN IST VERSICHERT")
                .contains("mein_vertrag_lesen");
        assertThat(prompts.get(1)).contains("ES IST NIEMAND ANGEMELDET")
                .doesNotContain("DIESE KUNDIN IST VERSICHERT");

        assertThat(prompts).allSatisfy(p -> assertThat(p)
                .contains("KEINE LEISTUNGSAUSSAGE OHNE FUNDSTELLE")
                .contains("DER BERATUNGSLEITFADEN IST INTERN")
                .contains("SIE SCHLIESSEN NICHTS AB")
                .contains("NICHT_BESTIMMBAR UND UNBEKANNT SIND KEINE ABLEHNUNG"));
    }

    @Test
    @DisplayName("for a logged-in Kundin the Vertrag is already in the system prompt")
    void theVertragIsReadBeforeTheModel() {
        List<String> prompts = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(_ -> "Antwort") {
                    @Override
                    public String respond(String modell, String systemprompt,
                                            List<ChatMessage> history, List<ToolCallback> tools) {
                        prompts.add(systemprompt);
                        return "Antwort";
                    }
                },
                MODELLE,
                _ -> List.of(new Vertragstool(
                        () -> seen.add(MandantContext.caller()))),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Welcher Tarif passt zu mir?", Map.of(), 10003L, reported::add);

        assertThat(prompts).singleElement().satisfies(prompt -> assertThat(prompt)
                .contains("DER VERTRAG DIESER KUNDIN")
                .contains("\"tarifId\":\"ATRA_DENT_X\"")
                .doesNotContain("\\\"tarifId\\\""));
        assertThat(seen).containsExactly(10003L);
        assertThat(reported.getFirst().protocol()).isEqualTo(Protocol.MCP);
        assertThat(reported.getFirst().operation()).isEqualTo("mein_vertrag_lesen");
        assertThat(withProtokoll(Protocol.MODELL)).first().satisfies(point ->
                assertThat(point.data()).containsEntry("vertragReadOut", true));
    }

    @Test
    @DisplayName("without a login no Vertrag is read out")
    void withoutLoginNoVertrag() {
        List<String> prompts = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(_ -> "Antwort") {
                    @Override
                    public String respond(String modell, String systemprompt,
                                            List<ChatMessage> history, List<ToolCallback> tools) {
                        prompts.add(systemprompt);
                        return "Antwort";
                    }
                },
                MODELLE,
                _ -> List.of(new Vertragstool(
                        () -> seen.add(MandantContext.caller()))),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Welche Tarife gibt es?", Map.of(), null, reported::add);

        assertThat(seen).isEmpty();
        assertThat(prompts).singleElement().satisfies(prompt ->
                assertThat(prompt).doesNotContain("DER VERTRAG DIESER KUNDIN"));
        assertThat(withProtokoll(Protocol.MCP)).isEmpty();
        assertThat(withProtokoll(Protocol.MODELL)).first().satisfies(point ->
                assertThat(point.data()).containsEntry("vertragReadOut", false));
    }

    @Test
    @DisplayName("if reading out fails, the conversation still runs")
    void aFailureWhileReadingOutDoesNotAbort() {
        List<String> prompts = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(_ -> "Antwort") {
                    @Override
                    public String respond(String modell, String systemprompt,
                                            List<ChatMessage> history, List<ToolCallback> tools) {
                        prompts.add(systemprompt);
                        return "Antwort";
                    }
                },
                MODELLE,
                _ -> List.of(new Vertragstool(() -> {
                    throw new IllegalStateException("Kernsystem antwortet nicht");
                })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10003L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(prompts).singleElement().satisfies(prompt ->
                assertThat(prompt).doesNotContain("DER VERTRAG DIESER KUNDIN"));
        assertThat(withProtokoll(Protocol.MODELL)).first().satisfies(point ->
                assertThat(point.data()).containsEntry("vertragReadOut", false));
    }

    @Test
    @DisplayName("the Kundennummer is bound to the thread during the tool call")
    void theKundennummerReachesTheToolCall() {
        List<Long> seen = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tools.getFirst().call("{}");
                    return "Antwort";
                }),
                MODELLE,
                _ -> List.of(new Testtool("mein_vertrag_lesen",
                        () -> seen.add(MandantContext.caller()))),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        assertThat(seen).containsExactly(10001L, 10001L);
        assertThat(MandantContext.caller()).isNull();
    }

    @Test
    @DisplayName("every real tool call reports an intermediate state")
    void toolCallsReportIntermediateStates() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tools.get(0).call("{}");
                    tools.get(1).call("{}");
                    return "Antwort";
                }),
                MODELLE,
                _ -> List.of(
                        new Testtool("mein_vertrag_lesen", () -> { }),
                        new Testtool("bedingungen_suchen", () -> { })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        assertThat(texts()).containsExactly(
                "sehe in Ihrem Vertrag nach", "sehe in Ihrem Vertrag nach",
                "sehe mir Ihre Frage an",
                "sehe in Ihrem Vertrag nach", "sehe in Ihrem Vertrag nach",
                "schlage in den Bedingungen nach", "schlage in den Bedingungen nach",
                "sehe mir Ihre Frage an",
                "lege Ihnen die Angaben daneben");

        List<TracePoint> viaMcp = withProtokoll(Protocol.MCP);
        assertThat(viaMcp).hasSize(6);
        assertThat(viaMcp.getFirst().peer()).isEqualTo("kernsystem");
        assertThat(viaMcp.getFirst().operation()).isEqualTo("mein_vertrag_lesen");
        assertThat(viaMcp.getFirst().data()).containsEntry("arguments", "{}");
        assertThat(viaMcp.get(1).data()).containsKey("result");
        assertThat(viaMcp.getLast().peer()).isEqualTo("wissen");
        assertThat(viaMcp.get(2).data()).containsEntry("call", 2);
        assertThat(viaMcp.get(3).data()).containsEntry("call", 2);
        assertThat(viaMcp.get(4).data()).containsEntry("call", 3);

        assertThat(withProtokoll(Protocol.MODELL).getFirst().label())
                .isEqualTo("Antwort erarbeiten");
        assertThat(viaMcp.getFirst().label()).isEqualTo("Vertrag nachschlagen");

        assertThat(reported).allSatisfy(point ->
                assertThat(point.sender()).isEqualTo(Beratungsagent.SENDER));
    }

    @Test
    @DisplayName("without a tool call only the model call reports")
    void withoutAToolOnlyTheModelCall() {
        Beratungsagent agent = agent(prompt -> "Antwort ohne Nachschlagen");

        agent.run("g1", "Hallo", Map.of(), 10001L, reported::add);

        assertThat(reported).hasSize(2).allSatisfy(point -> {
            assertThat(point.protocol()).isEqualTo(Protocol.MODELL);
            assertThat(point.peer()).isEqualTo("flash");
        });
        assertThat(reported.getFirst().data()).containsEntry("loggedIn", true);
    }

    @Test
    @DisplayName("the model call closes with a result of its own")
    void theModelCallCloses() {
        Beratungsagent agent = agent(_ -> "Antwort ohne Nachschlagen");

        agent.run("g1", "Hallo", Map.of(), 10001L, reported::add);

        List<TracePoint> viaModell = withProtokoll(Protocol.MODELL);
        assertThat(viaModell).hasSize(2);
        assertThat(viaModell.getFirst().operation()).isEqualTo("antworten");
        assertThat(viaModell.getFirst().data())
                .containsEntry("call", 1)
                .doesNotContainKey("result");
        assertThat(viaModell.getLast().operation()).isEqualTo("antworten");
        assertThat(viaModell.getLast().data())
                .containsEntry("call", 1)
                .containsKey("result");
    }

    @Test
    @DisplayName("a runaway loop yields a refusal, not half an answer")
    void aRunawayLoopYieldsARefusal() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    for (int i = 0; i < ObservedTools.MAX_COUNT + 5; i++) {
                        tools.getFirst().call("{}");
                    }
                    return "so weit gekommen bin ich nie";
                }),
                MODELLE,
                _ -> List.of(new Testtool("bedingungen_suchen", () -> { })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Erzaehl mir alles", Map.of(), 10001L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.answerText()).contains("nicht weiter");
        assertThat(result.answerText()).doesNotContain("so weit gekommen");
    }

    @Test
    @DisplayName("a tool that always throws runs into the limit too")
    void aThrowingToolRunsIntoTheLimit() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    for (int i = 0; i < ObservedTools.MAX_COUNT + 5; i++) {
                        try {
                            tools.getFirst().call("{}");
                        } catch (IllegalArgumentException _) {
                        }
                    }
                    return "so weit gekommen bin ich nie";
                }),
                MODELLE,
                _ -> List.of(new Testtool("bedingungen_suchen",
                        () -> {
                            throw new IllegalArgumentException("Unbekannter Wert");
                        })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Erzaehl mir alles", Map.of(), 10001L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(reported).filteredOn(point -> "toolschleife".equals(point.operation()))
                .singleElement()
                .satisfies(point -> assertThat(point.data())
                        .containsEntry("calls", ObservedTools.MAX_COUNT));
    }

    @Test
    @DisplayName("if the model fails, a refusal comes without technical wording")
    void aModelOutageYieldsARefusal() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(_ -> {
                    throw new ModelUnreachableException("flash",
                            new IllegalStateException("429 quota exceeded"));
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.answerText()).doesNotContain("flash").doesNotContain("429");
    }

    @Test
    @DisplayName("a runaway loop closes the model call as failed")
    void aRunawayLoopClosesTheModelCall() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    for (int i = 0; i < ObservedTools.MAX_COUNT + 5; i++) {
                        tools.getFirst().call("{}");
                    }
                    return "so weit gekommen bin ich nie";
                }),
                MODELLE,
                _ -> List.of(new Testtool("bedingungen_suchen", () -> { })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Erzaehl mir alles", Map.of(), 10001L, reported::add);

        List<TracePoint> viaModell = withProtokoll(Protocol.MODELL);
        assertThat(viaModell).hasSize(2);
        assertThat(viaModell.getLast().data())
                .containsEntry("call", 1)
                .containsKey("failed");
    }

    @Test
    @DisplayName("an unreachable model closes the model call as failed")
    void anUnreachableModelClosesTheModelCall() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(_ -> {
                    throw new ModelUnreachableException("flash",
                            new IllegalStateException("429 quota exceeded"));
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        List<TracePoint> viaModell = withProtokoll(Protocol.MODELL);
        assertThat(viaModell).hasSize(2);
        assertThat(viaModell.getLast().data())
                .containsEntry("call", 1)
                .containsEntry("reachable", false)
                .containsKey("failed");
    }

    @Test
    @DisplayName("if the model calls rueckfrage_stellen, the turn ends as an input-required")
    void anInputRequiredSignalBecomesTheOutcome() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, SignalTools.FOLLOW_UP)
                            .call("{\"frage\":\"Steht bei Ihnen eine Behandlung an?\"}");
                    return "Ich habe da noch eine Frage.";
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g1", "Welcher Tarif passt?", Map.of(), 10040L,
                reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(result.answerText()).isEqualTo("Steht bei Ihnen eine Behandlung an?");
    }

    @Test
    @DisplayName("a signal without following text is an input-required, not a failure")
    void aSignalWithoutFollowingTextIsNoFailure() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, SignalTools.FOLLOW_UP)
                            .call("{\"frage\":\"Wie alt sind Sie?\"}");
                    throw new ModelWithoutResponseException("flash");
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g1", "Welcher Tarif passt zu mir?", Map.of(),
                null, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(result.answerText()).isEqualTo("Wie alt sind Sie?");
    }

    @Test
    @DisplayName("a signal before a provider outage stays an input-required")
    void aSignalBeforeAProviderOutageStaysInputRequired() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, SignalTools.FOLLOW_UP)
                            .call("{\"frage\":\"Wie alt sind Sie?\"}");
                    throw new ModelUnreachableException("flash",
                            new IllegalStateException("503 backend unavailable"));
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g1", "Welcher Tarif passt zu mir?", Map.of(),
                10040L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(result.answerText()).isEqualTo("Wie alt sind Sie?");
    }

    @Test
    @DisplayName("if the model calls rueckfrage_stellen and then runs into the limit, it stays an "
            + "input-required")
    void theSignalAlsoAppliesInARunawayLoop() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, SignalTools.FOLLOW_UP)
                            .call("{\"frage\":\"Steht bei Ihnen eine Behandlung an?\"}");
                    ToolCallback other = tool(tools, "bedingungen_suchen");
                    for (int i = 0; i < ObservedTools.MAX_COUNT; i++) {
                        other.call("{}");
                    }
                    return "sollte nicht ankommen";
                }),
                MODELLE,
                _ -> List.of(new Testtool("bedingungen_suchen", () -> { })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g1", "Welcher Tarif passt?", Map.of(), 10040L,
                reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);
        assertThat(result.answerText()).isEqualTo("Steht bei Ihnen eine Behandlung an?");
    }

    @Test
    @DisplayName("if the model calls anliegen_ablehnen, the turn ends as a refusal")
    void aRefusalSignalBecomesTheOutcome() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, SignalTools.REJECT)
                            .call("{\"begruendung\":\"Zu einer anderen Person sage ich nichts.\"}");
                    throw new ModelWithoutResponseException("flash");
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g2", "Was hat mein Mann fuer einen Tarif?",
                Map.of(), 10040L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(result.answerText()).contains("anderen Person");
    }

    @Test
    @DisplayName("the input-required is in the History, otherwise the answer to it lacks context")
    void theInputRequiredIsInTheHistory() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, SignalTools.FOLLOW_UP)
                            .call("{\"frage\":\"Steht bei Ihnen eine Behandlung an?\"}");
                    throw new ModelWithoutResponseException("flash");
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g3", "Welcher Tarif passt?", Map.of(), 10040L, reported::add);

        assertThat(store.history("g3")).last()
                .extracting(ChatMessage::text).isEqualTo("Steht bei Ihnen eine Behandlung an?");
    }

    @Test
    @DisplayName("an unreachable model is a failure, not a refusal")
    void aModelFailureIsAFailure() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(_ -> {
                    throw new ModelUnreachableException("flash", new RuntimeException("weg"));
                }),
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g4", "Was zahlt mein Tarif?", Map.of(), 10040L,
                reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    @DisplayName("if the follow-up question takes a phrase from the Leitfaden, that is a failure, not an "
            + "input-required")
    void aSignalViolatingConfidentialityIsAFailure() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, ConfidentialityGuard.LEITFADEN).call("{}");
                    tool(tools, SignalTools.FOLLOW_UP).call(
                            "{\"frage\":\"Stellen Sie dem Monatsbeitrag den Eigenanteil einer "
                                    + "einzelnen?\"}");
                    throw new ModelWithoutResponseException("flash");
                }),
                MODELLE,
                _ -> List.of(new Testtool(ConfidentialityGuard.LEITFADEN,
                        () -> { }) {
                    @Override
                    public String call(String input) {
                        super.call(input);
                        return """
                                {"frage":"zu teuer","vertraulichkeit":"intern",
                                 "vertraulichkeitshinweis":"intern",
                                 "abschnitte":[{"dokumentId":"beratungshandbuch",
                                                "abschnittId":"einwand-teuer",
                                                "ueberschrift":"Einwand","text":"%s",
                                                "bewertung":0.9}]}
                                """.formatted(LEITFADEN_SENTENCE);
                    }
                }),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result = agent.run("g5", "Das ist mir zu teuer", Map.of(), 10041L,
                reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    @DisplayName("the history carries both sides and is forgotten when the context finishes")
    void theHistoryIsKeptAndForgotten() {
        Beratungsagent agent = agent(prompt -> "Ich empfehle balance.");

        agent.run("g1", "Was passt zu mir?", Map.of(), 10001L, reported::add);

        assertThat(store.history("g1"))
                .extracting(ChatMessage::text)
                .containsExactly("Was passt zu mir?", "Ich empfehle balance.");

        agent.finish("g1");
        assertThat(store.history("g1")).isEmpty();
    }

    @Test
    @DisplayName("the artifact carries the promised fields and the tools that were used")
    void theArtifactCarriesThePromisedFields() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tools.getFirst().call("{}");
                    return "Antwort";
                }),
                MODELLE,
                _ -> List.of(new Testtool("bedingungen_suchen", () -> { })),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        assertThat(result.artifactName()).isEqualTo("beratung");
        assertThat(result.content()).containsKeys("belege", "hinweise", "tools", "views");
        assertThat(result.content())
                .containsEntry("tools", List.of("bedingungen_suchen"))
                .containsEntry("views", List.of());
        assertThat(result.content().keySet())
                .containsExactly("belege", "hinweise", "tools", "views");
    }

    @Test
    @DisplayName("a view appears in the artifact and as its own point in the trace")
    @SuppressWarnings("unchecked")
    void theViewIsInTheArtifactAndInTheTrace() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, "tarifempfehlung").call("{}");
                    return "Ich empfehle Ihnen brillant.";
                }),
                MODELLE,
                _ -> List.of(new Empfehlungstool()),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Welcher Tarif passt zu mir?", Map.of(), 10001L, reported::add);

        List<Map<String, Object>> views =
                (List<Map<String, Object>>) result.content().get("views");
        assertThat(views).hasSize(1);
        assertThat(views.getFirst()).containsEntry("kind", "tarifempfehlung");

        assertThat(reported).extracting(TracePoint::operation).contains("views");
    }

    @Test
    @DisplayName("the Fundstelle of a Bedingungen search is in the artifact")
    @SuppressWarnings("unchecked")
    void belegeAreInTheArtifact() {
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, BelegCollector.BEDINGUNGEN).call("{}");
                    return "Ihr Tarif traegt 70 Prozent (§ 3 Leistungsumfang).";
                }),
                MODELLE,
                _ -> List.of(new Bedingungstool()),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult result =
                agent.run("g1", "Was zahlt mein Tarif?", Map.of(), 10001L, reported::add);

        List<Map<String, Object>> belege =
                (List<Map<String, Object>>) result.content().get("belege");
        assertThat(belege).hasSize(1);
        assertThat(belege.getFirst())
                .containsEntry("dokumentId", "bedingungen-balance")
                .containsEntry("abschnittId", "leistungsumfang");
    }

    @Test
    @DisplayName("without a login the model gets only the open tools")
    void withoutLoginOnlyOpenTools() {
        List<List<String>> offered = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    offered.add(tools.stream()
                            .map(w -> w.getToolDefinition().name()).toList());
                    return "Antwort";
                }),
                MODELLE,
                allowed -> ToolSelection.catalog().stream()
                        .filter(name -> allowed.allowed(name, ToolSelection.serverOf(name)))
                        .map(name -> (ToolCallback) new Testtool(name, () -> { })).toList(),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Welche Tarife gibt es?", Map.of(), null, reported::add);

        assertThat(offered.getFirst()).hasSize(10)
                .doesNotContain("mein_vertrag_lesen", "mein_beitrag_berechnen",
                        "meine_schadensfaelle_auflisten");

        List<String> expectedTools = Stream.concat(
                        ToolSelection.allowedFor(Beratungsmodus.NEUBERATUNG).stream(),
                        Stream.of(SignalTools.FOLLOW_UP, SignalTools.REJECT))
                .sorted()
                .toList();
        assertThat(reported.getFirst().data())
                .containsEntry("loggedIn", false)
                .containsEntry("tools", expectedTools);
    }

    @Test
    @DisplayName("if the first answer is copied, the second goes out")
    void aSecondAttemptAfterAViolation() {
        List<String> prompts = new ArrayList<>();
        List<Integer> toolCount = new ArrayList<>();
        Beratungsagent agent = withLeitfaden(prompts, toolCount,
                LEITFADEN_SENTENCE, "In eigenen Worten: Ihr Tarif traegt 70 Prozent (§ 3).");

        AgentResult result =
                agent.run("g1", "Das ist mir zu teuer", Map.of(), 10001L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.COMPLETED);
        assertThat(result.answerText()).isEqualTo(
                "In eigenen Worten: Ihr Tarif traegt 70 Prozent (§ 3).");

        assertThat(toolCount).containsExactly(3, 0);
        assertThat(prompts.get(1)).contains("HINWEIS ZU IHRER LETZTEN ANTWORT");
        assertThat(prompts.get(1)).doesNotContain("Eigenanteil einer einzelnen Krone");
    }

    @Test
    @DisplayName("if the second answer is copied too, nothing goes out at all")
    void copiedTwiceYieldsARefusal() {
        List<String> prompts = new ArrayList<>();
        Beratungsagent agent = withLeitfaden(prompts, new ArrayList<>(),
                LEITFADEN_SENTENCE, LEITFADEN_SENTENCE);

        AgentResult result =
                agent.run("g1", "Das ist mir zu teuer", Map.of(), 10001L, reported::add);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.answerText()).doesNotContain("Eigenanteil einer einzelnen Krone");
        assertThat(store.history("g1")).hasSize(1);
    }

    @Test
    @DisplayName("the Gesamtbetrag in the tool call comes from the extraction, not from the model")
    void gesamtbetragComesFromTheExtraction() {
        Einreichtool einreichen = new Einreichtool();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    tool(tools, BelegFidelity.EINREICHEN).call("""
                            {"behandlungsdatum":"2026-06-23",\
                            "positionen":[{"betrag":"773.68","beschreibung":"Vollkrone"}],\
                            "rechnung":{"gesamtbetrag":"773.68"}}""");
                    return "Der Fall ist eingereicht.";
                }),
                MODELLE, _ -> List.of(einreichen),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Hier ist meine Rechnung.", BELEG, 10001L, reported::add);

        assertThat(einreichen.totalAmount()).isEqualTo("1746.28");
        assertThat(reported).filteredOn(point -> "belegtreue".equals(point.operation()))
                .singleElement()
                .satisfies(point -> assertThat(point.data())
                        .containsEntry("tool", BelegFidelity.EINREICHEN)
                        .containsEntry("call", 1)
                        .containsEntry("totalFromModel", "773.68")
                        .containsEntry("totalFromBeleg", "1746.28"));
    }

    @Test
    @DisplayName("a second Rechnung in the same conversation suspends the correction instead of giving the "
            + "first the Betrag of the second")
    void aSecondRechnungSuspendsTheCorrection() {
        Map<String, Object> zweiterBeleg = Map.of(Beratungsmodus.BELEG, Map.of(
                "rechnungsnummer", "2026-4711",
                "gesamtbetrag", "420.00",
                "positionen", List.of(Map.of("ziffer", "2150", "leistung", "Inlay",
                        "betrag", "420.00"))));
        Einreichtool einreichen = new Einreichtool();
        List<Integer> turns = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    turns.add(turns.size());
                    if (turns.size() <= 2) {
                        tool(tools, SignalTools.FOLLOW_UP)
                                .call("{\"frage\":\"Zu welcher Rechnung gehoert das?\"}");
                        return "wird durch das Signal ersetzt";
                    }
                    tool(tools, BelegFidelity.EINREICHEN).call("""
                            {"behandlungsdatum":"2026-06-23",\
                            "positionen":[{"betrag":"999.99","beschreibung":"Kronen"}],\
                            "rechnung":{"gesamtbetrag":"999.99"}}""");
                    return "Der Fall ist eingereicht.";
                }),
                MODELLE, _ -> List.of(einreichen),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        agent.run("g1", "Hier ist meine Rechnung.", BELEG, 10001L, reported::add);
        agent.run("g1", "Und hier noch eine.", zweiterBeleg, 10001L, reported::add);
        agent.run("g1", "Zur zweiten, bitte.", Map.of(), 10001L, reported::add);

        assertThat(einreichen.totalAmount()).isEqualTo("999.99");
        assertThat(reported).filteredOn(point -> "belegtreue".equals(point.operation()))
                .singleElement()
                .satisfies(point -> assertThat(point.data())
                        .containsEntry("adopted", false)
                        .containsEntry("totalFromBeleg", "420.00")
                        .containsEntry("previousTotal", "1746.28"));
    }

    @Test
    @DisplayName("in the turn after an input-required the call carries the Betrag from the extraction too")
    void gesamtbetragSurvivesTheInputRequired() {
        Einreichtool einreichen = new Einreichtool();
        List<Integer> turns = new ArrayList<>();
        Beratungsagent agent = new Beratungsagent(
                new Testzugang(tools -> {
                    turns.add(turns.size());
                    if (turns.size() == 1) {
                        tool(tools, SignalTools.FOLLOW_UP)
                                .call("{\"frage\":\"Ist das Ihre eigene Rechnung?\"}");
                        return "wird durch das Signal ersetzt";
                    }
                    tool(tools, BelegFidelity.EINREICHEN).call("""
                            {"behandlungsdatum":"2026-06-23",\
                            "positionen":[{"betrag":"773.68","beschreibung":"Vollkrone"}],\
                            "rechnung":{"gesamtbetrag":"773.68"}}""");
                    return "Der Fall ist eingereicht.";
                }),
                MODELLE, _ -> List.of(einreichen),
                store, BELEGE, GUARD, VIEWS, MAPPER);

        AgentResult firstTurn =
                agent.run("g1", "Hier ist meine Rechnung.", BELEG, 10001L, reported::add);
        assertThat(firstTurn.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);

        agent.run("g1", "Ja, die ist von mir.", Map.of(), 10001L, reported::add);

        assertThat(einreichen.totalAmount()).isEqualTo("1746.28");
    }

    @Nested
    @DisplayName("intake of a Rechnung")
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = TestModel.WITHOUT_API_KEY)
    @Import({TestModel.class, TestTools.class})
    class AnnahmeEinerRechnung {

        @Autowired
        private Beratungsagent agent;

        @Autowired
        private TestModel.Aufrufprotokoll protokoll;

        @Test
        @DisplayName("with a Beleg and a login: intake prompt, two tools plus signals, the Beleg as JSON "
                + "in the user message")
        void intakeWithABeleg() {
            Map<String, Object> data = Map.of("rechnung", Map.of(
                    "rechnungsnummer", "2026-4711",
                    "positionen", List.of(Map.of("ziffer", "2150", "leistung", "Inlay",
                            "betrag", "420.00", "datum", "2026-01-08"))));

            agent.run("g-annahme", "Hier ist meine Rechnung.", data, 10001L,
                    StatusChannel.discarded());

            var call = protokoll.last();
            assertThat(call.systemprompt()).contains("RECHNUNG EINREICHEN");
            assertThat(call.tools()).containsExactlyInAnyOrder(
                    "mein_vertrag_lesen", "schadensfall_einreichen", "rueckfrage_stellen",
                    "anliegen_ablehnen");
            assertThat(call.history()).contains("BELEG (Extraktion").contains("2026-4711")
                    .contains("2150");
        }

        @Test
        @DisplayName("with a Beleg but without a login: neuberatung, no schadensfall_einreichen on offer")
        void aBelegWithoutLogin() {
            Map<String, Object> data = Map.of("rechnung", Map.of("rechnungsnummer", "2026-4711"));

            agent.run("g-anonym", "Hier ist meine Rechnung.", data, null,
                    StatusChannel.discarded());

            var call = protokoll.last();
            assertThat(call.systemprompt()).contains("anmelden");
            assertThat(call.tools()).doesNotContain("schadensfall_einreichen");
        }

        @Test
        @DisplayName("after a follow-up question without a fresh Beleg: intake stays intake, the BELEG "
                + "block appears only once in the history")
        void theInputRequiredSurvivesTheIntake() {
            Map<String, Object> data = Map.of("rechnung", Map.of(
                    "rechnungsnummer", "2026-4711",
                    "positionen", List.of(Map.of("ziffer", "2150", "leistung", "Inlay",
                            "betrag", "420.00", "datum", "2026-01-08"))));

            AgentResult firstTurn = agent.run("g-rueckfrage", "Hier ist meine Rechnung.",
                    data, 10001L, StatusChannel.discarded());
            assertThat(firstTurn.outcome()).isEqualTo(Outcome.INPUT_REQUIRED);

            agent.run("g-rueckfrage", "Nein, das ist meine eigene Rechnung.", Map.of(),
                    10001L, StatusChannel.discarded());

            var secondCall = protokoll.last();
            assertThat(secondCall.systemprompt()).contains("RECHNUNG EINREICHEN");
            assertThat(secondCall.tools()).contains("schadensfall_einreichen");
            assertThat(secondCall.history().split("BELEG \\(Extraktion", -1)).hasSize(2);
        }
    }


    private static final String LEITFADEN_SENTENCE =
            "Stellen Sie dem Monatsbeitrag den Eigenanteil einer einzelnen Krone gegenueber "
                    + "und fragen Sie, ob dieser Betrag im Ernstfall bereitstuende.";

    private Beratungsagent withLeitfaden(List<String> prompts, List<Integer> toolzahl,
                                        String... respond) {
        List<String> open = new ArrayList<>(List.of(respond));
        return new Beratungsagent(new ModelClient() {

            @Override
            public String classify(String modell, String systemprompt, String message) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                    List<ToolCallback> tools) {
                prompts.add(systemprompt);
                toolzahl.add(tools.size());
                tools.stream()
                        .filter(w -> w.getToolDefinition().name()
                                .equals(ConfidentialityGuard.LEITFADEN))
                        .forEach(w -> w.call("{}"));
                return open.removeFirst();
            }
        }, MODELLE, _ -> List.of(new Testtool(ConfidentialityGuard.LEITFADEN,
                () -> { }) {
            @Override
            public String call(String input) {
                super.call(input);
                return McpWrapper.um("""
                        {"frage":"zu teuer","vertraulichkeit":"intern",
                         "vertraulichkeitshinweis":"intern",
                         "abschnitte":[{"dokumentId":"beratungshandbuch",
                                        "abschnittId":"einwand-teuer",
                                        "ueberschrift":"Einwand","text":"%s","bewertung":0.9}]}
                        """.formatted(LEITFADEN_SENTENCE));
            }
        }), store, BELEGE, GUARD, VIEWS, MAPPER);
    }

    private Beratungsagent agent(Function<String, String> antwort) {
        return new Beratungsagent(
                new Testzugang(_ -> "Antwort") {
                    @Override
                    public String respond(String modell, String systemprompt,
                                            List<ChatMessage> history,
                                            List<ToolCallback> tools) {
                        return antwort.apply(systemprompt);
                    }
                },
                MODELLE, _ -> List.of(), store, BELEGE, GUARD, VIEWS, MAPPER);
    }

    private static ToolCallback tool(List<ToolCallback> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool nicht im Angebot: " + name));
    }

    private static class Testzugang implements ModelClient {

        private final Function<List<ToolCallback>, String> schleife;

        Testzugang(Function<List<ToolCallback>, String> schleife) {
            this.schleife = schleife;
        }

        @Override
        public String classify(String modell, String systemprompt, String message) {
            throw new UnsupportedOperationException("Der Beratungsagent ordnet nicht ein");
        }

        @Override
        public String respond(String modell, String systemprompt, List<ChatMessage> history,
                                List<ToolCallback> tools) {
            return schleife.apply(tools);
        }
    }

    private static class Testtool implements ToolCallback {

        private final String name;
        private final Runnable beiAufruf;

        Testtool(String name, Runnable beiAufruf) {
            this.name = name;
            this.beiAufruf = beiAufruf;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(name).description("Test")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
        }

        @Override
        public String call(String input) {
            beiAufruf.run();
            return "{}";
        }

        @Override
        public String call(String input, ToolContext context) {
            return call(input);
        }
    }

    private static final class Einreichtool extends Testtool {

        private String input;

        Einreichtool() {
            super(BelegFidelity.EINREICHEN, () -> { });
        }

        @Override
        public String call(String input) {
            this.input = input;
            return super.call(input);
        }

        String totalAmount() {
            return MAPPER.readTree(input).path("rechnung").path("gesamtbetrag").asString();
        }
    }

    private static class Vertragstool extends Testtool {

        private static final String AKTE = """
                {"id":10003,"vorname":"Clara","nachname":"Weiss",\
                "geburtsdatum":"1978-07-22","tarifId":"ATRA_DENT_X",\
                "versicherungsbeginn":"2025-02-01","vorversicherung":true,\
                "fehlendeZaehne":0,"status":"aktiv"}""";

        Vertragstool(Runnable beiAufruf) {
            super(ToolSelection.VERTRAG_READ, beiAufruf);
        }

        @Override
        public String call(String input) {
            super.call(input);
            return McpWrapper.um(AKTE);
        }
    }

    private static class Bedingungstool extends Testtool {

        private static final String TREFFER = """
                {"tarif":"ATRA_DENT_B","frage":"Leistungsumfang","treffer":[\
                {"dokumentId":"bedingungen-balance","abschnittId":"leistungsumfang",\
                "ueberschrift":"§ 3 Leistungsumfang","text":"70 Prozent fuer Zahnersatz.",\
                "bewertung":0.93,\
                "htmlUrl":"http://localhost:8082/dokumente/bedingungen-balance.html",\
                "pdfUrl":"http://localhost:8082/dokumente/bedingungen-balance.pdf"}]}""";

        Bedingungstool() {
            super(BelegCollector.BEDINGUNGEN, () -> { });
        }

        @Override
        public String call(String input) {
            super.call(input);
            return McpWrapper.um(TREFFER);
        }
    }

    private static class Empfehlungstool extends Testtool {

        private static final String ERGEBNIS = """
                {"weg":"BEDARF","zusammenfassung":"Der Bedarf entscheidet.",\
                "empfehlungen":[{"rang":1,"tarifschluessel":"ATRA_DENT_X",\
                "anzeigename":"atra.dent.brillant","kurzformel":"voller Schutz",\
                "reihenfolgeplatz":2,"needReasons":[],\
                "begruendung":"Weil der Leitfaden das so sagt.",\
                "hinweispflicht":"Woertlich aus dem Leitfaden."}],\
                "ausgeschlossene":[],"hinweise":[],"vertraulichkeit":"intern"}""";

        Empfehlungstool() {
            super("tarifempfehlung", () -> { });
        }

        @Override
        public String call(String input) {
            super.call(input);
            return McpWrapper.um(ERGEBNIS);
        }
    }
}
