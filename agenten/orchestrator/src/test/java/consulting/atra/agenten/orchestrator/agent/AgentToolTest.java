package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.client.AgentUnreachableException;
import consulting.atra.agenten.a2a.client.AgentClient;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolTest {

    private static final AgentCard BERATUNG_CARD = testCard("Beratungsagent");
    private static final long KUNDEN_ID = 10001L;

    private final ThreadRegistry threadRegistry = new ThreadRegistry();

    @Test
    void anUnknownAgentReportsTheAvailableNamesWithoutCallingTheClient() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry, _ -> {
            throw new AssertionError("Der Zugang haette bei unbekanntem Agent"
                    + " nicht erzeugt werden duerfen");
        });

        String response = withTask("gespraech-unbekannt",
                () -> tool.messageToAgent("Nichtagent", "Frage"));

        assertThat(response).contains("Unbekannter Agent").contains("Beratungsagent");
    }

    @Test
    void kundenIdFromTheTaskContextLandsInTheSendCallAndNotInTheToolParameters() {
        List<Aufruf> calls = new ArrayList<>();
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> recording(calls, Outcome.COMPLETED, "Antwort", "kontext-x", "task-x"));

        withTask("gespraech-kunde",
                () -> tool.messageToAgent("Beratungsagent", "Was zahlt mein Tarif?"));

        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().kundenId()).isEqualTo(KUNDEN_ID);
        assertThat(calls.getFirst().contextId()).isNull();
        assertThat(calls.getFirst().taskId()).isNull();
    }

    @Test
    void anInputRequiredIsRecordedAndTheSecondCallContinuesTheThread() {
        List<Aufruf> calls = new ArrayList<>();
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry, _ -> calls.isEmpty()
                ? recording(calls, Outcome.INPUT_REQUIRED, "Wie alt sind Sie?", "kontext-1", "task-1")
                : recording(calls, Outcome.COMPLETED, "Notiert", "kontext-1", "task-1"));

        String first = withTask("gespraech-rueckfrage",
                () -> tool.messageToAgent("Beratungsagent", "Welcher Tarif passt zu mir?"));

        assertThat(first).contains("Rueckfrage von Beratungsagent").contains("Wie alt sind Sie?");
        assertThat(threadRegistry.thread("gespraech-rueckfrage", "Beratungsagent"))
                .isEqualTo(new ThreadRegistry.Thread("kontext-1", "task-1"));

        withTask("gespraech-rueckfrage",
                () -> tool.messageToAgent("Beratungsagent", "42"));

        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).contextId()).isEqualTo("kontext-1");
        assertThat(calls.get(1).taskId()).isEqualTo("task-1");
        assertThat(threadRegistry.thread("gespraech-rueckfrage", "Beratungsagent")).isNull();
    }

    @Test
    void belegeOfACompletedAnswerLandInTheCollectorAndNotInTheToolResult() {
        Map<String, Object> data = Map.of("belege", List.of(Map.of("abschnitt", "§ 7 Abs. 2")));
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "Implantate sind mitversichert.",
                                "kontext-b", "task-b", data));
        ArztauskunftCollector folder = new ArztauskunftCollector();

        String response = TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "gespraech-beleg", StatusChannel.discarded(), folder),
                () -> tool.messageToAgent("Beratungsagent", "Was zahlt mein Tarif bei Implantaten?"));

        assertThat(response).contains("Implantate sind mitversichert.");
        assertThat(folder.content())
                .containsEntry(ArztauskunftCollector.SOURCE, "Beratungsagent")
                .containsEntry("belege", List.of(Map.of("abschnitt", "§ 7 Abs. 2")));
    }

    @Test
    void anInputRequiredDoesNotFillTheCollector() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.INPUT_REQUIRED, "Wie alt sind Sie?",
                                "kontext-c", "task-c"));
        ArztauskunftCollector folder = new ArztauskunftCollector();

        TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "gespraech-offen", StatusChannel.discarded(), folder),
                () -> tool.messageToAgent("Beratungsagent", "Welcher Tarif passt zu mir?"));

        assertThat(folder.content()).isEmpty();
    }

    @Test
    void theModelChoiceIsRecordedEvenIfTheNamePointsNowhere() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "Antwort", "kontext-w", "task-w"));
        Routings forwards = new Routings();

        TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "gespraech-wahl", StatusChannel.discarded(),
                        new ArztauskunftCollector(), forwards),
                () -> {
                    tool.messageToAgent("Beratungsagent", "Was zahlt mein Tarif?");
                    return tool.messageToAgent("Nichtagent", "Und sonst?");
                });

        assertThat(forwards.names())
                .containsExactly("Beratungsagent", "Nichtagent (unbekannt)");
    }

    @Test
    @DisplayName("a Rueckfrage to a signed-in Kundin points at the Akte before the Kundin")
    void theFollowUpMarkerNamesTheLookupWhenSomeoneIsSignedIn() {
        assertThat(AgentTool.followUp(KUNDEN_ID))
                .contains("kundendaten_nachschlagen")
                .contains("Geburtsdatum")
                .contains("ohne die Frage der Kundin zu zeigen");
    }

    @Test
    @DisplayName("without a sign-in there is nothing to look up, so the Kundin is asked")
    void theFollowUpMarkerAsksTheKundinWithoutASignIn() {
        assertThat(AgentTool.followUp(null))
                .isEqualTo("stelle sie der Kundin woertlich und beantworte sie nicht selbst");
    }

    @Test
    @DisplayName("a view reports its kind to the model and not a single value")
    void viewsReportTheirKindAndNoValue() {
        Map<String, Object> data = Map.of("views", List.of(
                Map.of("kind", "tarifvergleich",
                        "tarife", List.of(Map.of("schluessel", "ATRA_DENT_X",
                                "anzeigename", "atra.dent.brillant")))));
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "Der Unterschied liegt bei Implantaten.",
                                "kontext-d", "task-d", data));

        String response = withTask("gespraech-tabelle",
                () -> tool.messageToAgent("Beratungsagent", "balance oder brillant?"));

        assertThat(response)
                .contains("tarifvergleich")
                .contains("NICHT auf")
                .doesNotContain("ATRA_DENT_X")
                .doesNotContain("brillant");
    }

    @Test
    @DisplayName("without a view the tool result stays unchanged")
    void withoutAViewTheResultStaysUnchanged() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "Acht Monate Wartezeit.",
                                "kontext-e", "task-e", Map.of("views", List.of())));

        String response = withTask("gespraech-ohne",
                () -> tool.messageToAgent("Beratungsagent", "Gilt bei mir noch eine Wartezeit?"));

        assertThat(response).endsWith("Acht Monate Wartezeit.");
    }

    @Test
    void anUnreachableAgentReturnsReadableErrorTextInsteadOfThrowing() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) -> {
                    throw new AgentUnreachableException("Beratungsagent", "Verbindung abgelehnt");
                });

        String response = withTask("gespraech-ausfall",
                () -> tool.messageToAgent("Beratungsagent", "Frage"));

        assertThat(response).contains("Beratungsagent").contains("nicht erreichbar");
    }

    @Test
    @DisplayName("the same question is not asked twice to the same agent")
    void anIdenticalPairIsRejected() {
        List<Aufruf> calls = new ArrayList<>();
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> recording(calls, Outcome.REJECTED, "Nicht mein Fach.", "k", "t"));
        Routings forwards = new Routings();

        String second = TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "g", StatusChannel.discarded(),
                        new ArztauskunftCollector(), forwards),
                () -> {
                    tool.messageToAgent("Beratungsagent", "Frage");
                    return tool.messageToAgent("Beratungsagent", "Frage");
                });

        assertThat(calls).hasSize(1);
        assertThat(second).contains("schon gefragt");
    }

    @Test
    @DisplayName("even for an unknown agent the same question is not asked twice")
    void anIdenticalPairForAnUnknownAgentIsAlsoRejected() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry, _ -> {
            throw new AssertionError("Der Zugang haette bei unbekanntem Agent"
                    + " nicht erzeugt werden duerfen");
        });
        Routings forwards = new Routings();

        String second = TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "g", StatusChannel.discarded(),
                        new ArztauskunftCollector(), forwards),
                () -> {
                    tool.messageToAgent("Nichtagent", "Frage");
                    return tool.messageToAgent("Nichtagent", "Frage");
                });

        assertThat(second).contains("schon gefragt");
        assertThat(forwards.names()).containsExactly("Nichtagent (unbekannt)");
    }

    @Test
    @DisplayName("the same question rephrased gets through")
    void aNewWordingGetsThrough() {
        List<Aufruf> calls = new ArrayList<>();
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> recording(calls, Outcome.REJECTED, "Nicht mein Fach.", "k", "t"));

        TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "g", StatusChannel.discarded(),
                        new ArztauskunftCollector(), new Routings()),
                () -> {
                    tool.messageToAgent("Beratungsagent", "Frage");
                    return tool.messageToAgent("Beratungsagent", "Dieselbe Frage, enger gefasst");
                });

        assertThat(calls).hasSize(2);
    }

    @Test
    @DisplayName("after six calls it stops")
    void theCallCeilingStopsTheModel() {
        List<Aufruf> calls = new ArrayList<>();
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> recording(calls, Outcome.REJECTED, "Nicht mein Fach.", "k", "t"));

        String beyond = TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, "g", StatusChannel.discarded(),
                        new ArztauskunftCollector(), new Routings()),
                () -> {
                    String last = null;
                    for (int number = 1; number <= AgentTool.MAX_COUNT + 1; number++) {
                        last = tool.messageToAgent("Beratungsagent", "Frage " + number);
                    }
                    return last;
                });

        assertThat(AgentTool.MAX_COUNT).isEqualTo(6);
        assertThat(calls).hasSize(AgentTool.MAX_COUNT);
        assertThat(beyond).contains("genug");
    }

    @Test
    @DisplayName("a named artifact tells the model which kind of result came back")
    void theArtifactNameReachesTheModel() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Arztservice", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "Am Dienstag um 9 Uhr.",
                                "kontext-t", "task-t", Map.of(), "terminbestaetigung"));

        String response = withTask("gespraech-termin",
                () -> tool.messageToAgent("Arztservice", "Bitte einen Termin"));

        assertThat(response)
                .contains("Antwort von Arztservice")
                .contains("terminbestaetigung")
                .endsWith("Am Dienstag um 9 Uhr.");
    }

    @Test
    @DisplayName("a refusal and a booking reach the model as different markers")
    void aRefusalIsMarkedDifferentlyThanABooking() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Arztservice", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "In dem Zeitraum ist nichts frei.",
                                "kontext-a", "task-a", Map.of(), "terminauskunft"));

        String response = withTask("gespraech-absage",
                () -> tool.messageToAgent("Arztservice", "Bitte einen Termin"));

        assertThat(response).contains("terminauskunft").doesNotContain("terminbestaetigung");
    }

    @Test
    @DisplayName("without an artifact name the marker stays as it was")
    void withoutAnArtifactNameTheMarkerStaysUnchanged() {
        SubagentCatalog catalog = new SubagentCatalog(Map.of("Beratungsagent", BERATUNG_CARD));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> (_, _, _, _, _, _) ->
                        new SubagentResponse(Outcome.COMPLETED, "Acht Monate Wartezeit.",
                                "kontext-f", "task-f"));

        String response = withTask("gespraech-ohne-namen",
                () -> tool.messageToAgent("Beratungsagent", "Gilt bei mir noch eine Wartezeit?"));

        assertThat(response).isEqualTo("[Antwort von Beratungsagent - gib sie in eigener Stimme"
                + " wieder, Belege und Zahlen unveraendert]: Acht Monate Wartezeit.");
    }

    @Test
    @DisplayName("the task data goes to the subagent as data -- without the model knowing it")
    void theValuesAreCarriedAlong() {
        List<Aufruf> calls = new ArrayList<>();
        SubagentCatalog catalog = catalogWith(testCard("beratung"));
        AgentTool tool = new AgentTool(catalog, threadRegistry,
                _ -> recording(calls, Outcome.COMPLETED, "Fall 50071 eingereicht", "k1", "t1"));
        Map<String, Object> beleg = Map.of("rechnung", Map.of("rechnungsnummer", "2026-4711"));

        TaskContext.with(new TaskContext.Auftrag(KUNDEN_ID, "g1", StatusChannel.discarded(),
                        new ArztauskunftCollector(), new Routings(), beleg),
                () -> tool.messageToAgent("beratung", "Rechnung einreichen"));

        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().data()).isEqualTo(beleg);
    }


    private static String withTask(String contextId, Supplier<String> arbeit) {
        return TaskContext.with(
                new TaskContext.Auftrag(KUNDEN_ID, contextId, StatusChannel.discarded()), arbeit);
    }

    private static AgentClient recording(List<Aufruf> calls, Outcome outcome, String text,
                                              String responseKontextId, String responseTaskId) {
        return (contextId, taskId, anliegen, data, kundenId, _) -> {
            calls.add(new Aufruf(contextId, taskId, anliegen, data, kundenId));
            return new SubagentResponse(outcome, text, responseKontextId, responseTaskId);
        };
    }

    private record Aufruf(String contextId, String taskId, String anliegen, Map<String, Object> data,
                          Long kundenId) { }

    private static SubagentCatalog catalogWith(AgentCard... karten) {
        Map<String, AgentCard> map = new LinkedHashMap<>();
        for (AgentCard card : karten) {
            map.put(card.name(), card);
        }
        return new SubagentCatalog(map);
    }

    private static AgentCard testCard(String name) {
        return new AgentCard.Builder()
                .name(name)
                .description("Testkarte fuer " + name)
                .version("1.0.0")
                .url("http://localhost:0/")
                .protocolVersion("0.3.0")
                .capabilities(new AgentCapabilities.Builder().streaming(true).pushNotifications(false).build())
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .skills(List.of())
                .build();
    }
}
