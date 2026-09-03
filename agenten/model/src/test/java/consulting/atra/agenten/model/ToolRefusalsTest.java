package consulting.atra.agenten.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRefusalsTest {

    private static final String TOOL = "mein_beitrag_berechnen";

    private static final String REFUSAL =
            "Eintrittsalter 67 liegt ausserhalb der fuer ATRA_DENT_X zulaessigen Grenzen";

    @Test
    @DisplayName("a refused tool answers the model instead of ending the conversation")
    void aRefusalBecomesAnAnswer() {
        ToolCallback readable = ToolRefusals.readable(refusing(REFUSAL));

        String result = readable.call("{}");

        assertThat(asMap(result)).containsEntry("fehler", REFUSAL);
    }

    @Test
    @DisplayName("the answer is valid JSON -- the Gemini adapter parses every tool result")
    void theAnswerIsValidJson() {
        ToolCallback readable = ToolRefusals.readable(refusing(REFUSAL));

        String result = readable.call("{}");

        assertThatCode(() -> asMap(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("quotes and line breaks in the refusal stay inside the JSON")
    void aRefusalWithQuotesStaysReadable() {
        String awkward = "Unbekannter Status 'x'.\nErlaubt: \"eingereicht\", \"genehmigt\"";
        ToolCallback readable = ToolRefusals.readable(refusing(awkward));

        assertThat(asMap(readable.call("{}"))).containsEntry("fehler", awkward);
    }

    @Test
    @DisplayName("the method name Spring AI prefixes does not reach the model")
    void noJavaNoiseReachesTheModel() {
        ToolCallback readable = ToolRefusals.readable(refusing(REFUSAL));

        String message = String.valueOf(asMap(readable.call("{}")).get("fehler"));

        assertThat(message)
                .doesNotContain("Error invoking method")
                .doesNotContain("TextContent")
                .doesNotContain("annotations=")
                .doesNotContain("meta=null");
    }

    @Test
    @DisplayName("a successful call passes through untouched")
    void successPassesThrough() {
        ToolCallback readable = ToolRefusals.readable(answering("{\"beitrag\":\"24.90\"}"));

        assertThat(readable.call("{}")).isEqualTo("{\"beitrag\":\"24.90\"}");
    }

    @Test
    @DisplayName("the tool keeps its definition, so the model sees no difference")
    void theDefinitionSurvives() {
        ToolCallback original = answering("{}");

        ToolCallback readable = ToolRefusals.readable(original);

        assertThat(readable.getToolDefinition().name())
                .isEqualTo(original.getToolDefinition().name());
        assertThat(readable.getToolDefinition().inputSchema())
                .isEqualTo(original.getToolDefinition().inputSchema());
    }

    @Test
    @DisplayName("anything that is not a refusal still propagates")
    void otherFailuresStillPropagate() {
        ToolCallback readable = ToolRefusals.readable(new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition();
            }

            @Override
            public String call(String input) {
                throw new IllegalStateException("MCP-Verbindung zum Kernsystem abgebrochen");
            }
        });

        assertThatThrownBy(() -> readable.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("abgebrochen");
    }

    @Test
    @DisplayName("a list is converted in one go")
    void aWholeListIsConverted() {
        List<ToolCallback> readable =
                ToolRefusals.readable(List.of(refusing(REFUSAL), answering("{}")));

        assertThat(readable).hasSize(2);
        assertThat(asMap(readable.getFirst().call("{}"))).containsKey("fehler");
        assertThat(readable.getLast().call("{}")).isEqualTo("{}");
    }

    private static Map<String, Object> asMap(String json) {
        return JsonMapper.builder().build().readValue(json, new TypeReference<>() {
        });
    }

    private static ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(TOOL)
                .description("Testtool " + TOOL)
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .build();
    }

    private static ToolCallback refusing(String message) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition();
            }

            @Override
            public String call(String input) {
                return call(input, null);
            }

            @Override
            public String call(String input, ToolContext context) {
                throw new ToolExecutionException(definition(),
                        new IllegalStateException("Error calling tool: [TextContent["
                                + "annotations=null, text=Error invoking method: calculateOwnBeitrag\n"
                                + message + ", meta=null]]"));
            }
        };
    }

    private static ToolCallback answering(String answer) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition();
            }

            @Override
            public String call(String input) {
                return answer;
            }
        };
    }
}
