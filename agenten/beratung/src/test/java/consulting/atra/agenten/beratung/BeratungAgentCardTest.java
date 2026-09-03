package consulting.atra.agenten.beratung;

import consulting.atra.agenten.a2a.server.AgentCardLoader;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BeratungAgentCardTest {

    private static final AgentCard CARD =
            AgentCardLoader.load(Path.of("../cards/beratung.yaml"), "http://localhost:8085");

    @Test
    @DisplayName("no skill names a condition the caller cannot check")
    void skillsNameNoHeader() {
        assertThat(CARD.skills()).isNotEmpty().allSatisfy(skill ->
                assertThat(skill.description()).doesNotContain("x-kunden-id"));
    }

    @Test
    @DisplayName("the card carries all four skills")
    void fourSkills() {
        assertThat(CARD.skills()).extracting(AgentSkill::id)
                .containsExactlyInAnyOrder("bestandsberatung", "neuberatung", "rechnung_einreichen",
                        "kontaktdaten_aendern");
    }

    @Test
    @DisplayName("the Kontaktdaten skill promises the confirmation and names what stays out")
    void theKontaktdatenSkillNamesTheConfirmation() {
        AgentSkill skill = CARD.skills().stream()
                .filter(candidate -> "kontaktdaten_aendern".equals(candidate.id()))
                .findFirst().orElseThrow();

        assertThat(skill.description())
                .contains("ERST NACH BESTAETIGUNG")
                .contains("input-required")
                .contains("Geburtsdatum");
    }

    @Test
    @DisplayName("the card speaks of the wire format, not of a Drahtform")
    void wireFormatNotDrahtform() {
        assertThat(CARD.description()).doesNotContain("Drahtform").contains("Wire-Format");
    }
}
