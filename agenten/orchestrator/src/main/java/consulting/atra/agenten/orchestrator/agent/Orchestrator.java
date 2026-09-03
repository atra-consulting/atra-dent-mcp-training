package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.agent.AgentResult;
import consulting.atra.agenten.a2a.agent.Agent;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Orchestrator implements Agent {

    public static final String SENDER = "orchestrator";

    private static final int CLASSIFICATION = 1;

    static final String BELEG = "rechnung";

    static final String BELEG_HINT = "\n\n[Der Nachricht liegt eine hochgeladene Zahnarztrechnung als"
            + " Beleg bei (data-Part rechnung). Reiche sie unveraendert an den zustaendigen Agenten"
            + " weiter.]";

    private final ChatClient chatClient;
    private final AgentTool tool;
    private final KundendatenTool kundendatenTool;

    private final HymneTool hymneTool = new HymneTool();
    private final ThreadRegistry threadRegistry;
    private final SubagentCatalog catalog;
    private final String modell;

    public Orchestrator(ChatClient chatClient, AgentTool tool, KundendatenTool kundendatenTool,
                      ThreadRegistry threadRegistry, SubagentCatalog catalog, String modell) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.kundendatenTool = Objects.requireNonNull(kundendatenTool, "kundendatenTool");
        this.threadRegistry = Objects.requireNonNull(threadRegistry, "threadRegistry");
        this.catalog = Objects.requireNonNull(catalog, "katalog");
        this.modell = Objects.requireNonNull(modell, "modell");
    }

    @Override
    public AgentResult run(String contextId, String text, Map<String, Object> data,
                                      Long kundenId, StatusChannel status) {
        String systemPrompt = Orchestratorprompt.TEMPLATE.formatted(catalog.descriptions());

        boolean belegLiegtBei = data != null && data.get(BELEG) != null;
        String userText = belegLiegtBei ? text + BELEG_HINT : text;

        status.report(TracePoint.model(SENDER, modell, "einordnen",
                "ordne Ihr Anliegen ein", "Anliegen kategorisieren",
                classificationPointData(text, belegLiegtBei, catalog.names(), systemPrompt)));

        ArztauskunftCollector infos = new ArztauskunftCollector();
        Routings forwards = new Routings();

        return TaskContext.with(
                new TaskContext.Auftrag(kundenId, contextId, status, infos,
                        forwards, data == null ? Map.of() : data),
                () -> {
                    String response = chatClient.prompt()
                            .system(systemPrompt)
                            .user(userText)
                            .tools(tool, hymneTool, kundendatenTool)
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, contextId))
                            .call()
                            .content();

                    Map<String, Object> rueckweg = new LinkedHashMap<>();
                    rueckweg.put("call", CLASSIFICATION);
                    rueckweg.put("result", chosen(forwards));
                    status.report(TracePoint.model(SENDER, modell, "einordnen",
                            "ordne Ihr Anliegen ein", "Anliegen kategorisieren", rueckweg));

                    if (response == null || response.isBlank()) {
                        return AgentResult.failed(
                                "Der Dienst konnte die Nachricht nicht beantworten.");
                    }
                    return threadRegistry.isOpen(contextId)
                            ? AgentResult.inputRequired(response)
                            : AgentResult.completed("orchestrierung", response, infos.content());
                });
    }

    static Map<String, Object> classificationPointData(String message, boolean belegLiegtBei,
                                                  Collection<String> auswahl, String systemprompt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", CLASSIFICATION);
        data.put("message", TracePoint.truncate(message));
        data.put("belegAttached", belegLiegtBei);
        data.put("selection", auswahl.stream().sorted().toList());
        data.put("tool", AgentTool.TOOL);
        data.put("systemprompt", TracePoint.truncate(systemprompt));
        return data;
    }

    static String chosen(Routings weiterleitungen) {
        List<String> names = weiterleitungen.names();
        return names.isEmpty() ? "kein Fachagent — selbst beantwortet" : String.join(", ", names);
    }

    @Override
    public void finish(String contextId) {
        threadRegistry.clear(contextId);
    }
}
