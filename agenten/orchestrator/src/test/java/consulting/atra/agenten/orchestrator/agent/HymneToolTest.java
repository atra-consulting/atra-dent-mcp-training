package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.Protocol;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class HymneToolTest {

    @Test
    @DisplayName("the Hymne goes to the UI as a view")
    void theHymneLandsAsAViewInTheCollector() {
        ArztauskunftCollector folder = new ArztauskunftCollector();

        withTask(folder, StatusChannel.discarded(), () -> new HymneTool().playHymne());

        assertThat(folder.content()).containsExactly(
                Map.entry(ArztauskunftCollector.RENDERINGS, List.of(Map.of("kind", HymneTool.ART))));
    }

    @Test
    @DisplayName("the hang-up appears as an internal step in the trace")
    void theServiceReportsTheHangUp() {
        List<TracePoint> points = new ArrayList<>();

        withTask(new ArztauskunftCollector(), points::add, () -> new HymneTool().playHymne());

        assertThat(points).singleElement().satisfies(point -> {
            assertThat(point.protocol()).isEqualTo(Protocol.INTERNAL);
            assertThat(point.sender()).isEqualTo(Orchestrator.SENDER);
        });
    }

    @Test
    @DisplayName("the model learns that the Hymne is already playing")
    void theModelShouldNotStartSingingByItself() {
        String answer = withTask(new ArztauskunftCollector(), StatusChannel.discarded(),
                () -> new HymneTool().playHymne());

        assertThat(answer).contains("laeuft bereits");
    }

    private static String withTask(ArztauskunftCollector mappe, StatusChannel status,
                                     Supplier<String> arbeit) {
        return TaskContext.with(
                new TaskContext.Auftrag(10001L, "gespraech-hymne", status, mappe), arbeit);
    }
}
