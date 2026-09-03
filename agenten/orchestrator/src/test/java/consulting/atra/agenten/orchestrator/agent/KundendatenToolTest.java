package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.Protocol;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolPermission;
import consulting.atra.agenten.mcp.ToolSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KundendatenToolTest {

    private static final String AKTE = """
            {"id":10001,"vorname":"Anna","nachname":"Mueller",
             "geburtsdatum":"1990-04-12","email":"anna.mueller@example.com",
             "telefon":"+49 30 1234567",
             "adresse":{"strasse":"Hauptstr. 1","plz":"10115","ort":"Berlin","land":"DE"},
             "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2026-01-01",
             "vorversicherung":false,"fehlendeZaehne":0,"status":"aktiv"}""";

    @Test
    @DisplayName("the lookup returns exactly the six fields of the projection")
    void theProjectionCarriesTheSixFields() {
        String answer = lookedUp(new Akte(AKTE));

        assertThat(answer)
                .contains("\"vorname\":\"Anna\"")
                .contains("\"nachname\":\"Mueller\"")
                .contains("\"geburtsdatum\":\"1990-04-12\"")
                .contains("\"tarif\":\"ATRA_DENT_B\"")
                .contains("\"versicherungsbeginn\":\"2026-01-01\"")
                .contains("\"status\":\"aktiv\"");
    }

    @Test
    @DisplayName("the projection carries no address, e-mail, telephone or Kundennummer")
    void theProjectionLeavesTheContactDataBehind() {
        String answer = lookedUp(new Akte(AKTE));

        assertThat(answer)
                .doesNotContain("adresse")
                .doesNotContain("Hauptstr")
                .doesNotContain("10115")
                .doesNotContain("Berlin")
                .doesNotContain("email")
                .doesNotContain("example.com")
                .doesNotContain("telefon")
                .doesNotContain("1234567")
                .doesNotContain("10001");
    }

    @Test
    @DisplayName("the Kundennummer travels as the Mandant of the call, not in the answer")
    void theKundenIdTravelsAsTheMandant() {
        Akte akte = new Akte(AKTE);

        lookedUp(akte);

        assertThat(akte.mandant()).isEqualTo(10001L);
    }

    @Test
    @DisplayName("nobody signed in: the tool says so and invents nothing")
    void withoutALoginNothingIsLookedUp() {
        Akte akte = new Akte(AKTE);

        String answer = TaskContext.with(
                new TaskContext.Auftrag(null, "gespraech-anonym", StatusChannel.discarded()),
                () -> new KundendatenTool(source(akte)).readKundendaten());

        assertThat(answer).contains(KundendatenTool.NOBODY);
        assertThat(answer).doesNotContain("Anna");
        assertThat(akte.calls()).isZero();
    }

    @Test
    @DisplayName("an unreachable Kernsystem does not throw out of the tool call")
    void anUnreachableKernsystemStaysInside() {
        String answer = lookedUp(new Broken());

        assertThat(answer).contains(KundendatenTool.UNREADABLE);
    }

    @Test
    @DisplayName("a Kernsystem without the reading tool does not throw either")
    void aMissingSourceToolStaysInside() {
        String answer = TaskContext.with(
                new TaskContext.Auftrag(10001L, "gespraech-leer", StatusChannel.discarded()),
                () -> new KundendatenTool(allowed -> List.of()).readKundendaten());

        assertThat(answer).contains(KundendatenTool.UNREADABLE);
    }

    @Test
    @DisplayName("an answer without a single known field counts as unreadable")
    void anEmptyAkteCountsAsUnreadable() {
        String answer = lookedUp(new Akte("{\"id\":10001}"));

        assertThat(answer).contains(KundendatenTool.UNREADABLE);
    }

    @Test
    @DisplayName("the MCP content envelope is unpacked before the projection")
    void theContentEnvelopeIsUnpacked() {
        String packed = "[{\"type\":\"text\",\"text\":"
                + escaped(AKTE) + "}]";

        assertThat(lookedUp(new Akte(packed))).contains("\"tarif\":\"ATRA_DENT_B\"");
    }

    @Test
    @DisplayName("the lookup appears as an internal step in the trace")
    void theLookupIsVisibleInTheTimeline() {
        List<TracePoint> points = new ArrayList<>();

        TaskContext.with(
                new TaskContext.Auftrag(10001L, "gespraech-akte", points::add),
                () -> new KundendatenTool(source(new Akte(AKTE))).readKundendaten());

        assertThat(points).singleElement().satisfies(point -> {
            assertThat(point.protocol()).isEqualTo(Protocol.INTERNAL);
            assertThat(point.sender()).isEqualTo(Orchestrator.SENDER);
            assertThat(point.operation()).isEqualTo(KundendatenTool.TOOL);
            assertThat(point.data()).containsEntry("header x-kunden-id", 10001L);
            assertThat(point.data()).containsEntry("tool", KundendatenTool.SOURCE);
            assertThat(String.valueOf(point.data().get("result"))).contains("ATRA_DENT_B");
        });
    }

    @Test
    @DisplayName("the tool reads the Akte through the Kernsystem, not through another server")
    void theSourceIsTheKernsystem() {
        List<String> asked = new ArrayList<>();
        ToolSource source = allowed -> {
            asked.add(KundendatenTool.SOURCE + "@" + McpToolSource.KERNSYSTEM + "="
                    + allowed.allowed(KundendatenTool.SOURCE, McpToolSource.KERNSYSTEM));
            asked.add(KundendatenTool.SOURCE + "@" + McpToolSource.WISSEN + "="
                    + allowed.allowed(KundendatenTool.SOURCE, McpToolSource.WISSEN));
            asked.add("meine_kontaktdaten_aendern@" + McpToolSource.KERNSYSTEM + "="
                    + allowed.allowed("meine_kontaktdaten_aendern", McpToolSource.KERNSYSTEM));
            return List.of();
        };

        TaskContext.with(
                new TaskContext.Auftrag(10001L, "gespraech-quelle", StatusChannel.discarded()),
                () -> new KundendatenTool(source).readKundendaten());

        assertThat(asked).containsExactly(
                KundendatenTool.SOURCE + "@kernsystem=true",
                KundendatenTool.SOURCE + "@wissen=false",
                "meine_kontaktdaten_aendern@kernsystem=false");
    }

    private static String lookedUp(ToolCallback delegate) {
        return TaskContext.with(
                new TaskContext.Auftrag(10001L, "gespraech-akte", StatusChannel.discarded()),
                () -> new KundendatenTool(source(delegate)).readKundendaten());
    }

    private static ToolSource source(ToolCallback delegate) {
        return (ToolPermission allowed) ->
                allowed.allowed(KundendatenTool.SOURCE, McpToolSource.KERNSYSTEM)
                        ? List.of(delegate)
                        : List.of();
    }

    private static String escaped(String json) {
        StringBuilder text = new StringBuilder("\"");
        for (char sign : json.toCharArray()) {
            switch (sign) {
                case '"' -> text.append("\\\"");
                case '\n' -> text.append("\\n");
                default -> text.append(sign);
            }
        }
        return text.append('"').toString();
    }

    private static class Akte implements ToolCallback {

        private final String body;
        private final List<Long> mandanten = new ArrayList<>();

        Akte(String body) {
            this.body = body;
        }

        Long mandant() {
            return mandanten.isEmpty() ? null : mandanten.getFirst();
        }

        int calls() {
            return mandanten.size();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(KundendatenTool.SOURCE)
                    .description("Testtool")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build();
        }

        @Override
        public String call(String input) {
            mandanten.add(MandantContext.caller());
            return body;
        }

        @Override
        public String call(String input, ToolContext context) {
            return call(input);
        }
    }

    private static final class Broken extends Akte {

        Broken() {
            super("{}");
        }

        @Override
        public String call(String input) {
            throw new IllegalStateException("Kernsystem nicht erreichbar");
        }
    }
}
