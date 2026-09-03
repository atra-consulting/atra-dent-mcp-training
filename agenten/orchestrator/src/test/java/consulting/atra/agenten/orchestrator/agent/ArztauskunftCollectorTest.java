package consulting.atra.agenten.orchestrator.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArztauskunftCollectorTest {

    @Test
    void aFreshCollectorIsEmptyAndYieldsNoDataPart() {
        assertThat(new ArztauskunftCollector().content()).isEmpty();
    }

    @Test
    void theSourceComesBeforeTheAgentData() {
        ArztauskunftCollector folder = new ArztauskunftCollector();

        folder.adopt("Beratungsagent", Map.of("belege", List.of("§ 7")));

        assertThat(folder.content()).containsExactly(
                Map.entry(ArztauskunftCollector.SOURCE, "Beratungsagent"),
                Map.entry("belege", List.of("§ 7")));
    }

    @Test
    void anAgentWithoutAStructuredPartStillLeavesItsSource() {
        ArztauskunftCollector folder = new ArztauskunftCollector();

        folder.adopt("Arztservice", Map.of());

        assertThat(folder.content()).containsExactly(Map.entry(ArztauskunftCollector.SOURCE, "Arztservice"));
    }

    @Test
    void theSecondAuskunftReplacesTheFirstAndMixesNothingIn() {
        ArztauskunftCollector folder = new ArztauskunftCollector();
        folder.adopt("Beratungsagent", Map.of("belege", List.of("§ 7"), "hinweise", List.of("A")));

        folder.adopt("Arztservice", Map.of("quellenart", "sprachmodell"));

        assertThat(folder.content())
                .containsEntry(ArztauskunftCollector.SOURCE, "Arztservice")
                .containsEntry("quellenart", "sprachmodell")
                .doesNotContainKeys("belege", "hinweise");
    }

    @Test
    void aCollectedViewClaimsNoSource() {
        ArztauskunftCollector folder = new ArztauskunftCollector();

        folder.collectView(Map.of("kind", "hymne"));

        assertThat(folder.content()).containsExactly(
                Map.entry(ArztauskunftCollector.RENDERINGS, List.of(Map.of("kind", "hymne"))));
    }

    @Test
    void theContentCannotBeChangedFromOutside() {
        ArztauskunftCollector folder = new ArztauskunftCollector();
        folder.adopt("Beratungsagent", Map.of("belege", List.of("§ 7")));

        assertThatThrownBy(() -> folder.content().put("belege", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
