package consulting.atra.agenten.a2a.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TracePointTest {

    @Test
    @DisplayName("the label goes onto the wire and comes back")
    void labelRoundtrip() {
        TracePoint point = TracePoint.model("orchestrator", "gemini", "einordnen",
                "ordne Ihr Anliegen ein", "Anliegen kategorisieren",
                Map.of("nachricht", "n"));

        Map<String, Object> draht = point.asMap();
        assertThat(draht).containsEntry("label", "Anliegen kategorisieren");

        TracePoint read = TracePoint.fromMap(draht);
        assertThat(read.label()).isEqualTo("Anliegen kategorisieren");
    }

    @Test
    @DisplayName("without a label none appears on the wire either")
    void withoutALabel() {
        TracePoint point = TracePoint.plain("nur ein Text");

        assertThat(point.label()).isNull();
        assertThat(point.asMap()).doesNotContainKey("label");
        assertThat(TracePoint.fromMap(point.asMap()).label()).isNull();
    }

    @Test
    @DisplayName("a label that is not a string is not guessed")
    void anUnreadableLabel() {
        TracePoint read = TracePoint.fromMap(Map.of(
                "text", "t", "protocol", "INTERNAL", "label", 42));

        assertThat(read.label()).isNull();
    }

    @Test
    @DisplayName("withSenderIfAbsent keeps the label")
    void addingTheSenderKeepsTheLabel() {
        TracePoint point = TracePoint.fromMap(Map.of(
                "text", "t", "protocol", "MCP", "label", "Vertrag nachschlagen"));

        assertThat(point.withSenderIfAbsent("beratung").label())
                .isEqualTo("Vertrag nachschlagen");
    }

    @Test
    @DisplayName("a factory stamps the timestamp, and it goes onto the wire")
    void timestampRoundtrip() {
        Instant before = Instant.now();
        TracePoint point = TracePoint.tool("beratung", "kernsystem", "tarif_lesen",
                "sehe nach", "Tarif nachschlagen", Map.of());

        assertThat(point.timestamp()).isBetween(before, Instant.now());

        Map<String, Object> draht = point.asMap();
        assertThat(draht).containsEntry("timestamp", point.timestamp().toString());
        assertThat(TracePoint.fromMap(draht).timestamp()).isEqualTo(point.timestamp());
    }

    @Test
    @DisplayName("fromMap does not restamp -- a foreign point keeps its timestamp")
    void fromMapDoesNotRestampTheTimestamp() {
        Instant earlier = Instant.parse("2020-01-01T10:00:00Z");

        TracePoint read = TracePoint.fromMap(Map.of(
                "text", "t", "protocol", "MCP", "timestamp", earlier.toString()));

        assertThat(read.timestamp()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("an unreadable timestamp is not guessed")
    void anUnreadableTimestamp() {
        assertThat(TracePoint.fromMap(Map.of(
                "text", "t", "protocol", "INTERNAL", "timestamp", "gestern")).timestamp()).isNull();
        assertThat(TracePoint.fromMap(Map.of(
                "text", "t", "protocol", "INTERNAL", "timestamp", 42)).timestamp()).isNull();
    }

    @Test
    @DisplayName("without a timestamp none appears on the wire either")
    void withoutATimestamp() {
        TracePoint read = TracePoint.fromMap(Map.of("text", "t", "protocol", "INTERNAL"));

        assertThat(read.timestamp()).isNull();
        assertThat(read.asMap()).doesNotContainKey("timestamp");
    }

    @Test
    @DisplayName("withSenderIfAbsent keeps the timestamp")
    void addingTheSenderKeepsTheTimestamp() {
        Instant earlier = Instant.parse("2020-01-01T10:00:00Z");
        TracePoint point = TracePoint.fromMap(Map.of(
                "text", "t", "protocol", "MCP", "timestamp", earlier.toString()));

        assertThat(point.withSenderIfAbsent("beratung").timestamp()).isEqualTo(earlier);
    }
}
