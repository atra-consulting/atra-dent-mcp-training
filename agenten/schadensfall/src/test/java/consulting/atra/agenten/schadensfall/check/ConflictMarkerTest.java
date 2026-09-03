package consulting.atra.agenten.schadensfall.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictMarkerTest {

    private static final String AUS_FALSCHEM_STATUS =
            "Eine Bewertung ist nur aus in_pruefung moeglich, der Fall steht auf geprueft_freigabe";

    private static final String FINAL_STATE = "Status ausgezahlt ist ein Endzustand";

    private static final String VIA_MCP =
            "Error calling tool: [TextContent[text=Error invoking method: %s]]";

    @Test
    @DisplayName("both wordings of the transition matrix count as a conflict")
    void bothWordingsAreConflicts() {
        for (String wortlaut : List.of(AUS_FALSCHEM_STATUS, FINAL_STATE)) {
            assertThatThrownBy(() -> writeWith(VIA_MCP.formatted(wortlaut)))
                    .describedAs("Wortlaut: %s", wortlaut)
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    @DisplayName("the REST wording counts as a conflict too")
    void theRemainingWordingIsAConflict() {
        assertThatCode(() -> writeWith("409 Konflikt: Fall steht auf genehmigt"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("an ordinary tool error is NOT a conflict")
    void anOrdinaryErrorIsNoConflict() {
        assertThatThrownBy(() -> writeWith(
                VIA_MCP.formatted("bewertung.begruendung fehlt")))
                .isNotInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> writeWith("Connection refused: localhost:8080"))
                .isNotInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> writeWith(
                VIA_MCP.formatted("Schadensfall 50071 nicht gefunden")))
                .isNotInstanceOf(ConflictException.class);
    }

    private static void writeWith(String fehlertext) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(ToolSelection.BEWERTEN).description("Test")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
        ToolCallback tool = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String input) {
                return call(input, null);
            }

            @Override
            public String call(String input, ToolContext context) {
                throw new ToolExecutionException(definition,
                        new IllegalStateException(fehlertext));
            }
        };
        new BewertungWriter(JsonMapper.builder().build()).write(50071L, 4711L,
                new BewertungResult(Bewertungsvorschlag.FREIGABE, new BigDecimal("336.00"),
                        List.of(), List.of(), null, "Gedeckt."),
                0, List.of(), "modell", tool);
    }
}
