package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.beratung.agent.Beratungsmodus;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpProperties;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.model.ToolRefusals;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class McpToolSourceIT {

    private static final String KERNSYSTEM_KEY = "atra-lab-2026";

    private McpToolSource source;

    @BeforeEach
    void connect() {
        source = new McpToolSource(new McpProperties(
                new McpProperties.Connection("http://localhost:8080", "/mcp",
                        KERNSYSTEM_KEY),
                new McpProperties.Connection("http://localhost:8086", "/mcp", null),
                new McpProperties.Connection("http://localhost:8082", "/mcp", null),
                Duration.ofSeconds(30)));
    }

    @AfterEach
    void separate() {
        if (source != null) {
            source.close();
        }
    }

    @Test
    @DisplayName("all three servers offer the tools the Beratung needs")
    void theStartupCheckPasses() {
        source.check(Set.copyOf(ToolSelection.allExpected()));
    }

    @Test
    @DisplayName("logged in twelve tools, without login eight")
    void theSelectionDependsOnTheLoginState() {
        assertThat(names(bestandsberatung()))
                .containsExactlyInAnyOrderElementsOf(ToolSelection.allowedFor(Beratungsmodus.BESTANDSBERATUNG));
        assertThat(names(source.forPermission(ToolSelection.permission(Beratungsmodus.NEUBERATUNG))))
                .containsExactlyInAnyOrderElementsOf(ToolSelection.allowedFor(Beratungsmodus.NEUBERATUNG));

        assertThat(names(bestandsberatung()))
                .doesNotContain("meinen_tarif_wechseln", "schadensfall_einreichen");
    }

    @Test
    @DisplayName("the same connection, two Kundinnen, two Akten")
    void theMandantengrenzeHoldsOverTheRealConnection() {
        ToolCallback readVertrag = bestandsberatung().stream()
                .filter(w -> w.getToolDefinition().name().equals("mein_vertrag_lesen"))
                .findFirst().orElseThrow();

        String one = MandantContext.with(10001L, () -> readVertrag.call("{}"));
        String other = MandantContext.with(10002L, () -> readVertrag.call("{}"));

        assertThat(one).isNotEqualTo(other);
        assertThat(one).contains("Anna");
        assertThat(other).contains("Bernd");
    }

    @Test
    @DisplayName("without a Kundennummer a refusal, never a foreign Akte")
    void withoutAKundennummerARefusal() {
        ToolCallback readVertrag = ToolRefusals.readable(bestandsberatung().stream()
                .filter(w -> w.getToolDefinition().name().equals("mein_vertrag_lesen"))
                .findFirst().orElseThrow());

        String ohne = MandantContext.with(null, () -> readVertrag.call("{}"));

        assertThat(ohne).contains("Kundennummer").doesNotContain("Anna").doesNotContain("Bernd");
    }

    @Test
    @DisplayName("a refusal reaches the model as JSON, so the Gespraech goes on")
    void aRefusalIsReadableForTheModel() {
        ToolCallback beitrag = ToolRefusals.readable(bestandsberatung().stream()
                .filter(w -> w.getToolDefinition().name().equals("mein_beitrag_berechnen"))
                .findFirst().orElseThrow());

        String refused = MandantContext.with(10002L,
                () -> beitrag.call("{\"tarifId\":\"ATRA_DENT_X\"}"));

        assertThat(JsonMapper.builder().build().readTree(refused).get(ToolRefusals.ERROR))
                .isNotNull();
        assertThat(refused).contains("Eintrittsalter").doesNotContain("TextContent");
    }

    private List<ToolCallback> bestandsberatung() {
        return source.forPermission(ToolSelection.permission(Beratungsmodus.BESTANDSBERATUNG));
    }

    private static List<String> names(List<ToolCallback> tools) {
        return tools.stream().map(w -> w.getToolDefinition().name()).toList();
    }
}
