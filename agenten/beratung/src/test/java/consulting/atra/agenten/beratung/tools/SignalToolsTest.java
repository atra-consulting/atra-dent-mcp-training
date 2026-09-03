package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.a2a.agent.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignalToolsTest {

    @Test
    @DisplayName("without a call there is no signal")
    void withoutACallNoSignal() {
        assertThat(new SignalTools().called()).isEmpty();
    }

    @Test
    @DisplayName("the input-required carries its wording")
    void theInputRequiredCarriesTheWording() {
        SignalTools signals = new SignalTools();

        signals.askFollowUp("Steht eine Behandlung an?");

        assertThat(signals.called()).get()
                .isEqualTo(new SignalTools.Signal(Outcome.INPUT_REQUIRED, "Steht eine Behandlung an?"));
    }

    @Test
    @DisplayName("the rejection carries its Begruendung")
    void theRejectionCarriesTheBegruendung() {
        SignalTools signals = new SignalTools();

        signals.rejectAnliegen("Zu einer anderen Person sage ich nichts.");

        assertThat(signals.called()).get()
                .isEqualTo(new SignalTools.Signal(Outcome.REJECTED,
                        "Zu einer anderen Person sage ich nichts."));
    }

    @Test
    @DisplayName("the first call applies; a second changes nothing")
    void theFirstCallApplies() {
        SignalTools signals = new SignalTools();

        signals.askFollowUp("Steht eine Behandlung an?");
        signals.rejectAnliegen("Doch nicht.");

        assertThat(signals.called()).get()
                .extracting(SignalTools.Signal::art).isEqualTo(Outcome.INPUT_REQUIRED);
    }

    @Test
    @DisplayName("both tools come out as callbacks, under their names")
    void bothAsACallback() {
        List<ToolCallback> callbacks = new SignalTools().callbacks();

        assertThat(callbacks).extracting(cb -> cb.getToolDefinition().name())
                .containsExactlyInAnyOrder("rueckfrage_stellen", "anliegen_ablehnen");
    }
}
