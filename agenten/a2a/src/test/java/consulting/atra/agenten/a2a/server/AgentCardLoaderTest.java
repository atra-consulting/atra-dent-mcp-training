package consulting.atra.agenten.a2a.server;

import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCardLoaderTest {

    private static final Path CARD = Path.of("src/test/resources/card.yaml");

    @Test
    void readsNameStreamingAndSkills() {
        AgentCard card = AgentCardLoader.load(CARD, "http://localhost:8084");

        assertThat(card.name()).isEqualTo("Testagent");
        assertThat(card.capabilities().streaming()).isTrue();
        assertThat(card.skills()).isNotEmpty();
        assertThat(card.skills().getFirst().id()).isEqualTo("test-faehigkeit");
    }

    @Test
    void theUrlEndsWithASlash() {
        AgentCard card = AgentCardLoader.load(CARD, "http://localhost:8084");

        assertThat(card.url()).endsWith("/");
    }

    @Test
    void protocolVersionIs030() {
        AgentCard card = AgentCardLoader.load(CARD, "http://localhost:8084");

        assertThat(card.protocolVersion()).isEqualTo("0.3.0");
    }
}
