package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.a2a.server.AgentCardLoader;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchadensfallAgentCardTest {

    private static final AgentCard CARD =
            AgentCardLoader.load(Path.of("../cards/schadensfall.yaml"), "http://localhost:8087");

    @Test
    @DisplayName("the card loads and names the Schadensfallagent")
    void theCardLoads() {
        assertThat(CARD.name()).isEqualTo("atra.dent Schadensfallagent");
        assertThat(CARD.url()).isEqualTo("http://localhost:8087/");
        assertThat(CARD.protocolVersion()).isEqualTo("0.3.0");
        assertThat(CARD.capabilities().streaming()).isTrue();
    }

    @Test
    @DisplayName("exactly one skill, and it is fall_pruefen")
    void exactlyOneSkill() {
        assertThat(CARD.skills()).extracting(AgentSkill::id).containsExactly("fall_pruefen");
        assertThat(CARD.skills().getFirst().examples()).isNotEmpty();
    }

    @Test
    @DisplayName("no skill names a condition the caller cannot check")
    void skillsNameNoHeader() {
        assertThat(CARD.skills()).isNotEmpty().allSatisfy(skill ->
                assertThat(skill.description()).doesNotContain("x-kunden-id"));
    }

    @Test
    @DisplayName("the card promises that this agent does not decide finally")
    void noFinalDecision() {
        assertThat(CARD.description())
                .contains("Sachbearbeitung")
                .contains("geprueft_freigabe")
                .contains("geprueft_eskalation");
        assertThat(CARD.skills().getFirst().description())
                .contains("Setzt nie genehmigt, abgelehnt oder ausgezahlt");
    }
}
