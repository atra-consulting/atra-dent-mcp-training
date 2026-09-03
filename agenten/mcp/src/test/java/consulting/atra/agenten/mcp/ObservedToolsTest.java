package consulting.atra.agenten.mcp;

import consulting.atra.agenten.a2a.agent.Protocol;
import consulting.atra.agenten.a2a.agent.TracePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservedToolsTest {

    private static final String WITH_MANDANT = "akte_lesen";

    private static final String WITHOUT_MANDANT = "nachschlagen";

    private static final String LOCAL = "signal_setzen";

    private static final ToolMapping MAPPING = tool -> switch (tool) {
        case WITH_MANDANT -> McpToolSource.KERNSYSTEM;
        case WITHOUT_MANDANT -> McpToolSource.WISSEN;
        default -> null;
    };

    private static final ToolMessages MESSAGES = tool -> switch (tool) {
        case WITH_MANDANT -> new ObservedTools.Meldung("sehe in der Akte nach",
                "Akte nachschlagen");
        case WITHOUT_MANDANT -> new ObservedTools.Meldung("schlage nach", "Nachschlagen");
        case LOCAL -> new ObservedTools.Meldung("vermerke etwas", "Vermerken");
        default -> null;
    };

    private final List<TracePoint> reported = new ArrayList<>();
    private final ObservedTools store =
            new ObservedTools(reported::add, "agent", MAPPING, MESSAGES);

    @Test
    @DisplayName("a Kernsystem tool with a Mandant carries the header before the arguments")
    void kernsystemWithMandant() {
        ToolCallback wrapper = store.wrap(List.of(tool(WITH_MANDANT))).getFirst();

        MandantContext.with(7L, () -> wrapper.call("{}"));

        TracePoint hin = reported.getFirst();
        assertThat(hin.label()).isEqualTo("Akte nachschlagen");
        assertThat(hin.data()).containsEntry("header x-kunden-id", 7L);
        assertThat(hin.data().keySet())
                .containsExactly("header x-kunden-id", "call", "arguments");
    }

    @Test
    @DisplayName("a tool of another connection gets no headers entry")
    void anotherConnectionWithoutTheHeader() {
        ToolCallback wrapper = store.wrap(List.of(tool(WITHOUT_MANDANT))).getFirst();

        MandantContext.with(7L, () -> wrapper.call("{}"));

        assertThat(reported.getFirst().data()).doesNotContainKey("header x-kunden-id");
    }

    @Test
    @DisplayName("without a Mandant the headers entry stays out")
    void withoutAMandantNoHeader() {
        ToolCallback wrapper = store.wrap(List.of(tool(WITH_MANDANT))).getFirst();

        wrapper.call("{}");

        assertThat(reported.getFirst().data()).doesNotContainKey("header x-kunden-id");
    }

    @Test
    @DisplayName("a tool without a connection reports as an internal step, not as MCP")
    void aToolWithoutAServerIsInternal() {
        ToolCallback wrapper = store.wrap(List.of(tool(LOCAL))).getFirst();

        wrapper.call("{}");

        TracePoint hin = reported.getFirst();
        assertThat(hin.protocol()).isEqualTo(Protocol.INTERNAL);
        assertThat(hin.peer()).isNull();
        assertThat(hin.text()).isEqualTo("vermerke etwas");
        assertThat(hin.label()).isEqualTo("Vermerken");
    }

    @Test
    @DisplayName("without a message the tool name appears, and nothing invented")
    void withoutAMessageTheName() {
        ToolCallback wrapper = store.wrap(List.of(tool("unbekannt"))).getFirst();

        wrapper.call("{}");

        TracePoint hin = reported.getFirst();
        assertThat(hin.text()).isEqualTo("rufe unbekannt auf");
        assertThat(hin.label()).isNull();
    }

    @Test
    @DisplayName("a call carries its arguments along, untruncated")
    void callsCarryTheirArguments() {
        String arguments = "{\"schluessel\":\"A\",\"datum\":\"1990-04-01\"}";
        ToolCallback wrapper = store.wrap(
                List.of(tool(WITHOUT_MANDANT, "{\"betrag\":\"24.90\"}"))).getFirst();

        wrapper.call(arguments);

        List<ObservedTools.Toolaufruf> calls = store.callsOf(WITHOUT_MANDANT);
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().arguments()).isEqualTo(arguments);
        assertThat(calls.getFirst().content()).isEqualTo("{\"betrag\":\"24.90\"}");
    }

    @Test
    @DisplayName("asked for another tool, the list stays empty")
    void callsFromAnotherToolAreEmpty() {
        store.wrap(List.of(tool(WITH_MANDANT, "[]"))).getFirst().call("{}");

        assertThat(store.callsOf(WITHOUT_MANDANT)).isEmpty();
    }

    @Test
    @DisplayName("a tool that always throws still runs into the upper limit")
    void failedCallsCountToo() {
        ToolCallback wrapper = store.wrap(List.of(throwingTool(WITHOUT_MANDANT))).getFirst();

        for (int i = 0; i < ObservedTools.MAX_COUNT; i++) {
            assertThatThrownBy(() -> wrapper.call("{}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThatThrownBy(() -> wrapper.call("{}"))
                .isInstanceOf(ObservedTools.ToolschleifeAusgeufertException.class);
    }

    @Test
    @DisplayName("a failed call appears as failed in the trace and not in calls()")
    void aFailedCallIsNoResult() {
        ToolCallback wrapper = store.wrap(List.of(throwingTool(WITHOUT_MANDANT))).getFirst();

        assertThatThrownBy(() -> wrapper.call("{}"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(store.calls()).isEmpty();
        assertThat(store.attempts()).isEqualTo(1);
        assertThat(reported).hasSize(2);
        TracePoint back = reported.getLast();
        assertThat(back.data()).doesNotContainKey("result");
        assertThat(back.data()).containsEntry("call", 1);
        assertThat(String.valueOf(back.data().get("failed")))
                .contains("Eskalation (dringend)");
    }

    private static ToolCallback tool(String name) {
        return tool(name, "{}");
    }

    private static ToolCallback throwingTool(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description("Testtool " + name)
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String input) {
                throw new IllegalArgumentException("Unbekannte Empfehlung: Eskalation (dringend)");
            }
        };
    }

    private static ToolCallback tool(String name, String antwort) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description("Testtool " + name)
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                        .build();
            }

            @Override
            public String call(String input) {
                return McpWrapper.um(antwort);
            }
        };
    }
}
