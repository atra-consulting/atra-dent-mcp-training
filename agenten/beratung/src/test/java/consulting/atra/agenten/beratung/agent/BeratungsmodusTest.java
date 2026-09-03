package consulting.atra.agenten.beratung.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BeratungsmodusTest {

    @Test
    @DisplayName("logged in with a Beleg is intake; logged in without a Beleg is bestandsberatung; without a login always neuberatung")
    void determine() {
        Map<String, Object> mitBeleg = Map.of("rechnung", Map.of("rechnungsnummer", "1"));
        assertThat(Beratungsmodus.determine(true, mitBeleg)).isEqualTo(Beratungsmodus.RECHNUNG_EINREICHEN);
        assertThat(Beratungsmodus.determine(true, Map.of())).isEqualTo(Beratungsmodus.BESTANDSBERATUNG);
        assertThat(Beratungsmodus.determine(true, null)).isEqualTo(Beratungsmodus.BESTANDSBERATUNG);
        assertThat(Beratungsmodus.determine(false, mitBeleg)).isEqualTo(Beratungsmodus.NEUBERATUNG);
        assertThat(Beratungsmodus.determine(false, Map.of())).isEqualTo(Beratungsmodus.NEUBERATUNG);
    }

    @Test
    void loggedInEverythingButNeuberatung() {
        assertThat(Beratungsmodus.NEUBERATUNG.loggedIn()).isFalse();
        assertThat(Beratungsmodus.BESTANDSBERATUNG.loggedIn()).isTrue();
        assertThat(Beratungsmodus.RECHNUNG_EINREICHEN.loggedIn()).isTrue();
    }
}
