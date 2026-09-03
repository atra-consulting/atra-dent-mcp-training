package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskContextTest {

    @Test
    void aNullValueInTheDataDoesNotThrow() {
        Map<String, Object> data = new HashMap<>();
        data.put("rechnung", null);

        TaskContext.Auftrag task = new TaskContext.Auftrag(10001L, "g1",
                StatusChannel.discarded(), new ArztauskunftCollector(), new Routings(), data);

        assertThat(task.data()).containsEntry("rechnung", null);
    }

    @Test
    void theValuesCannotBeChangedFromOutside() {
        Map<String, Object> data = new HashMap<>();
        data.put("rechnung", Map.of("rechnungsnummer", "2026-4711"));

        TaskContext.Auftrag task = new TaskContext.Auftrag(10001L, "g1",
                StatusChannel.discarded(), new ArztauskunftCollector(), new Routings(), data);

        assertThatThrownBy(() -> task.data().put("rechnung", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingValuesBecomeAnEmptyMap() {
        TaskContext.Auftrag task = new TaskContext.Auftrag(10001L, "g1",
                StatusChannel.discarded(), new ArztauskunftCollector(), new Routings(), null);

        assertThat(task.data()).isEmpty();
    }
}
