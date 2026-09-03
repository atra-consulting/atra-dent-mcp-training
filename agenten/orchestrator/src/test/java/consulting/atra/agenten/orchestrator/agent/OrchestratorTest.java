package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrchestratorTest {

    @Test
    void finishRemovesTheOpenThreadOfTheContext() {
        ThreadRegistry threadRegistry = new ThreadRegistry();
        threadRegistry.record("gespraech-x", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Wie alt sind Sie?", "kontext", "task"));
        SubagentCatalog catalog = new SubagentCatalog(Map.of());
        AgentTool tool = new AgentTool(catalog, threadRegistry, card -> null);
        Orchestrator orchestrator = new Orchestrator(mock(ChatClient.class), tool, lookup(), threadRegistry, catalog, "gemini-test");

        orchestrator.finish("gespraech-x");

        assertThat(threadRegistry.isOpen("gespraech-x")).isFalse();
        assertThat(threadRegistry.thread("gespraech-x", "Beratungsagent")).isNull();
    }

    @Test
    void withoutRoutingAnsweringItselfIsTheResult() {
        assertThat(Orchestrator.chosen(new Routings()))
                .isEqualTo("kein Fachagent — selbst beantwortet");
    }

    @Test
    void severalRoutingsAppearInTheOrderTheyWereChosen() {
        Routings forwards = new Routings();
        forwards.add("Beratungsagent", "Frage 1");
        forwards.add("Schadensfallagent", "Frage 2");

        assertThat(Orchestrator.chosen(forwards))
                .isEqualTo("Beratungsagent, Schadensfallagent");
    }

    @Test
    void theClassificationPointShowsTheSelectionAndTheModelPrompt() {
        Map<String, Object> data = Orchestrator.classificationPointData(
                "Welcher Tarif passt zu mir?", false,
                Set.of("Schadensfallagent", "atra.dent Beratungsagent"),
                "Du bist atra.denta ... === VERFUEGBARE AGENTEN === ...");

        assertThat(data.get("message")).isEqualTo("Welcher Tarif passt zu mir?");
        assertThat(data.get("belegAttached")).isEqualTo(false);
        assertThat(data.get("selection"))
                .isEqualTo(List.of("Schadensfallagent", "atra.dent Beratungsagent"));
        assertThat(data.get("tool")).isEqualTo(AgentTool.TOOL);
        assertThat(data.get("systemprompt").toString()).contains("VERFUEGBARE AGENTEN");
        assertThat(data.keySet())
                .containsExactly("call", "message", "belegAttached", "selection", "tool",
                        "systemprompt");
    }

    @Test
    void theClassificationPointShowsWhetherABelegIsAttached() {
        Map<String, Object> data = Orchestrator.classificationPointData(
                "Bitte pruefen Sie meine Rechnung", true, Set.of(), "systemprompt");

        assertThat(data.get("belegAttached")).isEqualTo(true);
    }

    @Test
    void longClassificationValuesAreVisiblyTruncated() {
        String tooLong = "x".repeat(TracePoint.MAX_LENGTH + 1);

        Map<String, Object> data = Orchestrator.classificationPointData(tooLong, false, Set.of(), tooLong);

        assertThat(data.get("message").toString()).endsWith("Zeichen");
        assertThat(data.get("systemprompt").toString()).endsWith("Zeichen");
    }

    @Test
    void finishLeavesOtherContextsUntouched() {
        ThreadRegistry threadRegistry = new ThreadRegistry();
        threadRegistry.record("gespraech-y", "Beratungsagent",
                new SubagentResponse(Outcome.INPUT_REQUIRED, "Frage", "kontext", "task"));
        SubagentCatalog catalog = new SubagentCatalog(Map.of());
        AgentTool tool = new AgentTool(catalog, threadRegistry, card -> null);
        Orchestrator orchestrator = new Orchestrator(mock(ChatClient.class), tool, lookup(), threadRegistry, catalog, "gemini-test");

        orchestrator.finish("eine-andere-konversation");

        assertThat(threadRegistry.isOpen("gespraech-y")).isTrue();
    }

    @Test
    void whenABelegIsAttachedTheBelegHintGoesToTheModel() {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient chatClient = promptStub(spec, "Antwort");
        SubagentCatalog catalog = new SubagentCatalog(Map.of());
        ThreadRegistry threadRegistry = new ThreadRegistry();
        AgentTool tool = new AgentTool(catalog, threadRegistry, card -> null);
        Orchestrator orchestrator = new Orchestrator(chatClient, tool, lookup(), threadRegistry, catalog, "gemini-test");

        orchestrator.run("gespraech-beleg", "Bitte pruefen Sie meine Rechnung",
                Map.of("rechnung", Map.of("rechnungsnummer", "2026-4711")), 10001L,
                StatusChannel.discarded());

        verify(spec).user(contains("[Der Nachricht liegt eine hochgeladene Zahnarztrechnung"));
    }

    @Test
    void withoutABelegTheUserTextStaysUnchanged() {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient chatClient = promptStub(spec, "Antwort");
        SubagentCatalog catalog = new SubagentCatalog(Map.of());
        ThreadRegistry threadRegistry = new ThreadRegistry();
        AgentTool tool = new AgentTool(catalog, threadRegistry, card -> null);
        Orchestrator orchestrator = new Orchestrator(chatClient, tool, lookup(), threadRegistry, catalog, "gemini-test");

        orchestrator.run("gespraech-ohne-beleg", "Welcher Tarif passt zu mir?", Map.of(), 10001L,
                StatusChannel.discarded());

        verify(spec).user("Welcher Tarif passt zu mir?");
    }

    @Test
    void aNullValueUnderRechnungCountsAsNoBeleg() {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient chatClient = promptStub(spec, "Antwort");
        SubagentCatalog catalog = new SubagentCatalog(Map.of());
        ThreadRegistry threadRegistry = new ThreadRegistry();
        AgentTool tool = new AgentTool(catalog, threadRegistry, card -> null);
        Orchestrator orchestrator = new Orchestrator(chatClient, tool, lookup(), threadRegistry, catalog, "gemini-test");
        Map<String, Object> data = new HashMap<>();
        data.put("rechnung", null);

        orchestrator.run("gespraech-null-beleg", "Welcher Tarif passt zu mir?", data, 10001L,
                StatusChannel.discarded());

        verify(spec).user("Welcher Tarif passt zu mir?");
    }

    @Test
    void theModelCanChooseBetweenTheAgentPathTheHymneAndTheLookup() {
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient chatClient = promptStub(spec, "Antwort");
        SubagentCatalog catalog = new SubagentCatalog(Map.of());
        ThreadRegistry threadRegistry = new ThreadRegistry();
        AgentTool tool = new AgentTool(catalog, threadRegistry, card -> null);
        Orchestrator orchestrator = new Orchestrator(chatClient, tool, lookup(), threadRegistry, catalog, "gemini-test");
        ArgumentCaptor<Object> tools = ArgumentCaptor.forClass(Object.class);

        orchestrator.run("gespraech-werkzeuge", "Welcher Tarif passt zu mir?", Map.of(), 10001L,
                StatusChannel.discarded());

        verify(spec).tools(tools.capture(), tools.capture(), tools.capture());
        assertThat(tools.getAllValues())
                .contains(tool)
                .hasAtLeastOneElementOfType(HymneTool.class)
                .hasAtLeastOneElementOfType(KundendatenTool.class);
    }

    private static KundendatenTool lookup() {
        return new KundendatenTool(allowed -> List.of());
    }

    private static ChatClient promptStub(ChatClient.ChatClientRequestSpec spec, String antwort) {
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.tools(any(), any(), any())).thenReturn(spec);
        when(spec.advisors(any(Consumer.class))).thenReturn(spec);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(spec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(antwort);
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenReturn(spec);
        return chatClient;
    }
}
