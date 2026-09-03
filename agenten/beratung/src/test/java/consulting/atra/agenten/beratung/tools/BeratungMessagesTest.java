package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.beratung.agent.Beratungsmodus;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.McpWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BeratungMessagesTest {

    @Test
    @DisplayName("every allowed tool has text and a label")
    void messagesComplete() {
        for (String name : ToolSelection.allowedFor(Beratungsmodus.BESTANDSBERATUNG)) {
            check(name);
        }
    }

    @Test
    @DisplayName("the two signal tools have text and a label too")
    void signalToolsHaveMessages() {
        Stream.of(SignalTools.FOLLOW_UP, SignalTools.REJECT)
                .forEach(BeratungMessagesTest::check);
    }

    private static void check(String name) {
        List<TracePoint> points = new ArrayList<>();
        ObservedTools single = new ObservedTools(
                points::add, "beratung", ToolSelection.MAPPING, BeratungMessages.ALLE);
        single.wrap(List.of(tool(name))).getFirst().call("{}");

        TracePoint hin = points.getFirst();
        assertThat(hin.text()).as(name).doesNotStartWith("rufe ");
        assertThat(hin.label()).as(name).isNotBlank();
    }

    private static ToolCallback tool(String name) {
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
                return McpWrapper.um("{}");
            }
        };
    }
}
