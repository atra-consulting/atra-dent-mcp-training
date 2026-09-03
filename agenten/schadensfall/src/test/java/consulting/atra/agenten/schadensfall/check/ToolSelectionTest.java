package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSelectionTest {

    @Test
    @DisplayName("the model gets exactly the six reading and calculating tools")
    void theModelGetsSix() {
        ToolPermission allowed = ToolSelection.forModel();

        assertThat(allowed.allowed(ToolSelection.VERTRAG, McpToolSource.KERNSYSTEM)).isTrue();
        assertThat(allowed.allowed(ToolSelection.SCHADENSFAELLE, McpToolSource.KERNSYSTEM)).isTrue();
        assertThat(allowed.allowed(ToolSelection.ERSTATTUNG, McpToolSource.RECHENKERN)).isTrue();
        assertThat(allowed.allowed(ToolSelection.GOZ, McpToolSource.WISSEN)).isTrue();
        assertThat(allowed.allowed(ToolSelection.BEDINGUNGEN, McpToolSource.WISSEN)).isTrue();
        assertThat(allowed.allowed(ToolSelection.COMPARISON, McpToolSource.WISSEN)).isTrue();

        assertThat(ToolSelection.namesForModel()).hasSize(6);
    }

    @Test
    @DisplayName("the model does not get schadensfall_bewerten -- on no connection")
    void theModelDoesNotGetTheWritingTool() {
        ToolPermission allowed = ToolSelection.forModel();

        assertThat(allowed.allowed(ToolSelection.BEWERTEN, McpToolSource.KERNSYSTEM)).isFalse();
        assertThat(allowed.allowed(ToolSelection.BEWERTEN, McpToolSource.RECHENKERN)).isFalse();
        assertThat(allowed.allowed(ToolSelection.BEWERTEN, McpToolSource.WISSEN)).isFalse();
        assertThat(ToolSelection.namesForModel()).doesNotContain(ToolSelection.BEWERTEN);
    }

    @Test
    @DisplayName("the code gets exactly schadensfall_bewerten and nothing else")
    void theCodeGetsOnlyTheWritingTool() {
        ToolPermission allowed = ToolSelection.forCode();

        assertThat(allowed.allowed(ToolSelection.BEWERTEN, McpToolSource.KERNSYSTEM)).isTrue();
        assertThat(allowed.allowed(ToolSelection.VERTRAG, McpToolSource.KERNSYSTEM)).isFalse();
        assertThat(allowed.allowed(ToolSelection.ERSTATTUNG, McpToolSource.RECHENKERN)).isFalse();
        assertThat(allowed.allowed(ToolSelection.GOZ, McpToolSource.WISSEN)).isFalse();
    }

    @Test
    @DisplayName("a tool on the wrong connection is not allowed")
    void theWrongConnectionDoesNotCount() {
        assertThat(ToolSelection.forModel().allowed(ToolSelection.VERTRAG, McpToolSource.WISSEN))
                .isFalse();
        assertThat(ToolSelection.forModel().allowed(ToolSelection.GOZ, "irgendwas")).isFalse();
        assertThat(ToolSelection.forCode().allowed(ToolSelection.BEWERTEN, "irgendwas")).isFalse();
    }

    @Test
    @DisplayName("the startup check requires both roles together")
    void allExpectedIsTheUnion() {
        assertThat(ToolSelection.allExpected())
                .containsExactlyInAnyOrder(ToolSelection.VERTRAG, ToolSelection.SCHADENSFAELLE,
                        ToolSelection.ERSTATTUNG, ToolSelection.GOZ, ToolSelection.BEDINGUNGEN,
                        ToolSelection.COMPARISON, ToolSelection.BEWERTEN);
    }

    @Test
    @DisplayName("the local tools appear in no MCP permission")
    void localToolsAreNotMcpTools() {
        assertThat(ToolSelection.allExpected())
                .doesNotContain(ToolSelection.ARZT, ToolSelection.SIGNAL);
        assertThat(ToolSelection.forModel().allowed(ToolSelection.ARZT, McpToolSource.KERNSYSTEM))
                .isFalse();
    }

    @Test
    @DisplayName("the mapping names the connection of every MCP tool and none for the local ones")
    void theMappingKnowsTheConnections() {
        assertThat(SchadensfallMapping.ALLE.serverOf(ToolSelection.VERTRAG))
                .isEqualTo(McpToolSource.KERNSYSTEM);
        assertThat(SchadensfallMapping.ALLE.serverOf(ToolSelection.BEWERTEN))
                .isEqualTo(McpToolSource.KERNSYSTEM);
        assertThat(SchadensfallMapping.ALLE.serverOf(ToolSelection.ERSTATTUNG))
                .isEqualTo(McpToolSource.RECHENKERN);
        assertThat(SchadensfallMapping.ALLE.serverOf(ToolSelection.COMPARISON))
                .isEqualTo(McpToolSource.WISSEN);
        assertThat(SchadensfallMapping.ALLE.serverOf(ToolSelection.ARZT)).isNull();
        assertThat(SchadensfallMapping.ALLE.serverOf(ToolSelection.SIGNAL)).isNull();
        assertThat(SchadensfallMapping.ALLE.serverOf("gibt_es_nicht")).isNull();
    }

    @Test
    @DisplayName("there is a message for every tool of this agent")
    void theMessagesAreComplete() {
        for (String tool : ToolSelection.allExpected()) {
            assertThat(SchadensfallMessages.ALLE.forTool(tool))
                    .withFailMessage("Keine Meldung fuer %s", tool)
                    .isNotNull();
        }
        assertThat(SchadensfallMessages.ALLE.forTool(ToolSelection.ARZT)).isNotNull();
        assertThat(SchadensfallMessages.ALLE.forTool(ToolSelection.SIGNAL)).isNotNull();
        assertThat(SchadensfallMessages.ALLE.forTool("gibt_es_nicht")).isNull();
    }
}
