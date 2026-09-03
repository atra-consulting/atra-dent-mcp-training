package consulting.atra.agenten.a2a.tracelog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import consulting.atra.agenten.a2a.agent.Protocol;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TracelogTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("without a directory nothing is written and nothing created")
    void withoutTheFlagItIsOff() {
        assertThat(Tracelog.forDirectory(null, "beratung").isEnabled()).isFalse();
        assertThat(Tracelog.forDirectory("  ", "beratung").isEnabled()).isFalse();
        Tracelog.forDirectory(null, "beratung").write("trace", "g1", Map.of("a", "b"));
    }

    @Test
    @DisplayName("every service writes into its own file")
    void oneFilePerService(@TempDir Path directory) throws IOException {
        Tracelog.forDirectory(directory.toString(), "beratung")
                .write("trace", "g1", Map.of("operation", "tarif_lesen"));
        Tracelog.forDirectory(directory.toString(), "orchestrator")
                .write("trace", "g1", Map.of("operation", "einordnen"));

        assertThat(directory.resolve("beratung.jsonl")).exists();
        assertThat(directory.resolve("orchestrator.jsonl")).exists();
        assertThat(Files.readString(directory.resolve("beratung.jsonl")))
                .contains("tarif_lesen")
                .doesNotContain("einordnen");
    }

    @Test
    @DisplayName("every line is readable JSON on its own with timestamp, service and context")
    void oneLinePerEvent(@TempDir Path directory) throws IOException {
        Tracelog tracelog = Tracelog.forDirectory(directory.toString(), "beratung");
        tracelog.write("trace", "g1", Map.of("operation", "eins"));
        tracelog.write("trace", "g1", Map.of("operation", "zwei"));

        List<String> lines = Files.readAllLines(directory.resolve("beratung.jsonl"));

        assertThat(lines).hasSize(2);
        assertThat(asImage(lines.getFirst()))
                .containsEntry("service", "beratung")
                .containsEntry("kind", "trace")
                .containsEntry("context", "g1")
                .containsEntry("operation", "eins")
                .containsKey("timestamp");
    }

    @Test
    @DisplayName("a multiline value stays on one line")
    void multilineValuesDoNotBreakTheFormat(@TempDir Path directory) throws IOException {
        Tracelog.forDirectory(directory.toString(), "beratung")
                .write("trace", "g1", Map.of("ergebnis", "erste\nzweite\ndritte"));

        List<String> lines = Files.readAllLines(directory.resolve("beratung.jsonl"));

        assertThat(lines).hasSize(1);
        assertThat(asImage(lines.getFirst()))
                .containsEntry("ergebnis", "erste\nzweite\ndritte");
    }

    private static Map<String, Object> asImage(String line) throws IOException {
        return JSON.readValue(line, new TypeReference<Map<String, Object>>() { });
    }
}

class TracelogChannelTest {

    @Test
    @DisplayName("without a tracelog the same channel comes back unchanged")
    void withoutATracelogNoWrapper() {
        StatusChannel delegate = StatusChannel.discarded();

        assertThat(TracelogChannel.wrap(delegate, Tracelog.OFF, "g1")).isSameAs(delegate);
    }

    @Test
    @DisplayName("the point goes into the file and still on to the channel")
    void writesAndPassesOn(@TempDir Path directory) throws IOException {
        List<TracePoint> reported = new ArrayList<>();
        Tracelog tracelog = Tracelog.forDirectory(directory.toString(), "beratung");
        StatusChannel channel = TracelogChannel.wrap(reported::add, tracelog, "g1");

        TracePoint point = TracePoint.tool("beratung", "kernsystem", "tarif_lesen",
                "sehe den Tarif nach", "Tarif nachschlagen", Map.of("aufruf", 1));
        channel.report(point);

        assertThat(reported).containsExactly(point);

        String line = Files.readAllLines(directory.resolve("beratung.jsonl")).getFirst();
        assertThat(line)
                .contains("\"protocol\":\"" + Protocol.MCP.name() + "\"")
                .contains("\"sender\":\"beratung\"")
                .contains("\"peer\":\"kernsystem\"")
                .contains("\"operation\":\"tarif_lesen\"")
                .contains("\"context\":\"g1\"");
    }
}
