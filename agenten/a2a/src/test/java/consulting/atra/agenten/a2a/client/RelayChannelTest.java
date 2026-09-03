package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelayChannelTest {

    private final List<TracePoint> reported = new ArrayList<>();

    private final StatusChannel relay = RelayChannel.wrap(reported::add);

    @Test
    void aPointDeliveredTwiceIsReportedOnce() {
        TracePoint point = TracePoint.tool("beratung", "wissen", "bedingungen_suchen",
                "schlage in den Bedingungen nach", "Bedingungen durchsuchen",
                Map.of("call", 1));

        relay.report(point);
        relay.report(TracePoint.fromMap(point.asMap()));

        assertThat(reported).containsExactly(point);
    }

    @Test
    void twoPointsOfTheirOwnBothGoThrough() {
        TracePoint call = TracePoint.tool("beratung", "wissen", "bedingungen_suchen",
                "schlage in den Bedingungen nach", "Bedingungen durchsuchen",
                Map.of("call", 1));
        TracePoint back = TracePoint.tool("beratung", "wissen", "bedingungen_suchen",
                "schlage in den Bedingungen nach", "Bedingungen durchsuchen",
                Map.of("call", 1, "result", "§ 4"));

        relay.report(call);
        relay.report(back);

        assertThat(reported).containsExactly(call, back);
    }

    @Test
    void aPointRepeatedAfterAnotherOneGoesThrough() {
        TracePoint first = TracePoint.tool("beratung", "wissen", "bedingungen_suchen",
                "schlage in den Bedingungen nach", "Bedingungen durchsuchen",
                Map.of("call", 1));
        TracePoint between = TracePoint.internal("beratung", "views",
                "lege Ihnen die Angaben daneben", "Darstellungen zusammenstellen", Map.of());

        relay.report(first);
        relay.report(between);
        relay.report(TracePoint.fromMap(first.asMap()));

        assertThat(reported).hasSize(3);
    }
}
