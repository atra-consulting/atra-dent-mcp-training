package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.a2a.client.AgentLookup;
import consulting.atra.agenten.a2a.client.Subagent;
import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.client.SubagentProperties;
import consulting.atra.agenten.schadensfall.check.ArztTool;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArztAccessTest {

    private static final SubagentProperties PROPERTIES = new SubagentProperties(
            Map.of("arztservice", new Subagent("https://arzt.example", "geheim")),
            Duration.ofMinutes(4), Duration.ofSeconds(60));

    private final A2aServerConfiguration configuration = new A2aServerConfiguration();

    @Test
    @DisplayName("the agent offering the skill is the one that gets asked")
    void findsTheAgentByItsSkill() {
        AgentLookup lookup = configuration.subagents(
                catalogOf(cardWith("Fremder Auskunftsdienst", ArztTool.SKILL)), PROPERTIES);

        assertThat(lookup.withSkill(ArztTool.SKILL)).isPresent();
    }

    @Test
    @DisplayName("an agent that does not offer the skill is not asked instead")
    void doesNotFallBackToAnyOtherAgent() {
        AgentLookup lookup = configuration.subagents(
                catalogOf(cardWith("Irgendein Agent", "etwas-ganz-anderes")), PROPERTIES);

        assertThat(lookup.withSkill(ArztTool.SKILL)).isEmpty();
    }

    @Test
    @DisplayName("no agent at all leaves the lookup empty, and the tool is never offered")
    void staysEmptyWithoutAnyAgent() {
        AgentLookup lookup = configuration.subagents(catalogOf(), PROPERTIES);

        assertThat(lookup.withSkill(ArztTool.SKILL)).isEmpty();
    }

    private static SubagentCatalog catalogOf(AgentCard... cards) {
        return new SubagentCatalog(List.of(cards).stream()
                .collect(java.util.stream.Collectors.toMap(AgentCard::name, card -> card)));
    }

    private static AgentCard cardWith(String name, String skillId) {
        return new AgentCard.Builder()
                .name(name)
                .description("Testkarte")
                .version("1.0.0")
                .url("https://arzt.example/")
                .protocolVersion("0.3.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(false).pushNotifications(false).build())
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .skills(List.of(new AgentSkill.Builder()
                        .id(skillId)
                        .name(skillId)
                        .description("Testfertigkeit")
                        .tags(List.of())
                        .build()))
                .securitySchemes(Map.of())
                .build();
    }
}
