package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.McpWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProtokollCollectorTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final Clock CLOCK = Clock.systemUTC();

    @Test
    @DisplayName("one entry per tool call, and the guard last")
    void oneEntryPerCallAndTheGuard() {
        ObservedTools observed = observed(List.of(
                new Antwort(ToolSelection.VERTRAG, VERTRAG),
                new Antwort(ToolSelection.GOZ, GOZ),
                new Antwort(ToolSelection.ERSTATTUNG, ERSTATTUNG)));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, freigabe(), IMAGES, CLOCK);

        assertThat(protokoll).hasSize(4);
        assertThat(steps(protokoll)).containsExactly(
                "Vertrag gelesen", "GOZ-Prüfung", "Erstattung berechnet",
                ProtokollCollector.GUARD);
        assertThat(protokoll).allSatisfy(entry ->
                assertThat(entry).containsEntry("akteur", "agent"));
        assertThat(protokoll).allSatisfy(entry ->
                assertThat(OffsetDateTime.parse((String) entry.get("zeitpunkt"))).isNotNull());
    }

    @Test
    @DisplayName("the timestamps come from the supplied clock")
    void theTimestampComesFromTheClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC);
        ObservedTools observed = observed(List.of(new Antwort(ToolSelection.GOZ, GOZ)));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, freigabe(), IMAGES, clock);

        assertThat(protokoll).allSatisfy(entry ->
                assertThat(entry).containsEntry("zeitpunkt", "2026-08-18T12:00:00Z"));
        assertThat(agentCall(protokoll, 0)).containsEntry("zeitpunkt", "2026-08-18T12:00:00Z");
    }

    @Test
    @DisplayName("every entry carries its call as a trace")
    void theTraceForTheCall() {
        ObservedTools observed = observed(List.of(
                new Antwort(ToolSelection.GOZ, GOZ),
                new Antwort(ToolSelection.ARZT, ARZT)));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, freigabe(), IMAGES, CLOCK);

        Map<String, Object> gozCall = agentCall(protokoll, 0);
        assertThat(gozCall).containsEntry("art", "tool").containsEntry("name", ToolSelection.GOZ);
        assertThat((String) gozCall.get("ergebnisKurz")).contains("2197");
        assertThat(agentCall(protokoll, 1)).containsEntry("art", "a2a");
        assertThat(protokoll.getLast()).doesNotContainKey("calls");
    }

    @Test
    @DisplayName("input and result appear truncated in the trace")
    void theTraceIsTruncated() {
        String longResult = "{\"text\":\"" + "x".repeat(1000) + "\"}";
        ObservedTools observed = observed(List.of(
                new Antwort(ToolSelection.BEDINGUNGEN, longResult, "y".repeat(1000))));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, freigabe(), IMAGES, CLOCK);

        Map<String, Object> agentCall = agentCall(protokoll, 0);
        assertThat((String) agentCall.get("ergebnisKurz")).hasSizeLessThanOrEqualTo(201);
        assertThat((String) agentCall.get("eingabeKurz")).hasSizeLessThanOrEqualTo(201);
        assertThat((String) agentCall.get("ergebnisKurz")).endsWith("…");
    }

    @Test
    @DisplayName("the detail says at a glance what came out")
    void detailsAreShortAndConcrete() {
        ObservedTools observed = observed(List.of(
                new Antwort(ToolSelection.VERTRAG, VERTRAG),
                new Antwort(ToolSelection.GOZ, GOZ),
                new Antwort(ToolSelection.ERSTATTUNG, ERSTATTUNG),
                new Antwort(ToolSelection.ARZT, ARZT),
                new Antwort(ToolSelection.SIGNAL, "\"vermerkt\"",
                        "{\"empfehlung\":\"freigabe\",\"erstattungsvorschlag\":\"336.00\"}")));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, freigabe(), IMAGES, CLOCK);

        assertThat(details(protokoll)).containsExactly(
                "Tarif ATRA_DENT_B, Vertrag aktiv",
                "2 von 2 ENTHALTEN",
                "Erstattung 336.00 EUR",
                "plausibel / ueblich",
                "freigabe, 336.00 EUR",
                "Freigabe");
    }

    @Test
    @DisplayName("on a Eskalation the codes are in the guard entry")
    void theGuardEntryNamesTheCodes() {
        ObservedTools observed = observed(List.of(new Antwort(ToolSelection.GOZ, GOZ)));
        BewertungResult eskalation = new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                List.of(new Eskalationsgrund(Eskalationsgrund.GOZ_UNCLEAR, "…"),
                        new Eskalationsgrund(Eskalationsgrund.DUPLICATE, "…")),
                List.of(), null, "Bitte ansehen.");

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, eskalation, IMAGES, CLOCK);

        assertThat(protokoll.getLast()).containsEntry("detail",
                "Eskalation: GOZ_UNCLEAR, DUPLICATE");
    }

    @Test
    @DisplayName("an unreadable result does not cost the entry")
    void anUnreadableResultCostsNoEntry() {
        ObservedTools observed = observed(List.of(
                new Antwort(ToolSelection.ERSTATTUNG, "<html>Fehlerseite</html>")));

        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed, freigabe(), IMAGES, CLOCK);

        assertThat(protokoll).hasSize(2);
        assertThat(protokoll.getFirst()).containsEntry("schritt", "Erstattung berechnet");
        assertThat(protokoll.getFirst().get("detail")).isNull();
    }

    @Test
    @DisplayName("without a single call the guard entry remains")
    void withoutCallsOnlyTheGuard() {
        List<Map<String, Object>> protokoll =
                ProtokollCollector.aus(observed(List.of()), freigabe(), IMAGES, CLOCK);

        assertThat(protokoll).hasSize(1);
        assertThat(protokoll.getFirst()).containsEntry("schritt", ProtokollCollector.GUARD);
    }


    private static BewertungResult freigabe() {
        return new BewertungResult(Bewertungsvorschlag.FREIGABE, new BigDecimal("336.00"),
                List.of(), List.of(), null, "Alles gedeckt.");
    }

    private static List<String> steps(List<Map<String, Object>> protokoll) {
        return protokoll.stream().map(entry -> (String) entry.get("schritt")).toList();
    }

    private static List<String> details(List<Map<String, Object>> protokoll) {
        return protokoll.stream().map(entry -> (String) entry.get("detail")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> agentCall(List<Map<String, Object>> protokoll, int entry) {
        return ((List<Map<String, Object>>) protokoll.get(entry).get("calls")).getFirst();
    }

    private record Antwort(String tool, String result, String arguments) {

        Antwort(String tool, String result) {
            this(tool, result, "{}");
        }
    }

    private static ObservedTools observed(List<Antwort> respond) {
        var observed = new ObservedTools(StatusChannel.discarded(), "schadensfall",
                _ -> null, _ -> null);
        for (Antwort antwort : respond) {
            ToolCallback wrapped = observed.wrap(List.of(tool(antwort))).getFirst();
            wrapped.call(antwort.arguments());
        }
        return observed;
    }

    private static ToolCallback tool(Antwort antwort) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(antwort.tool()).description("Test")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
            }

            @Override
            public String call(String input) {
                return call(input, null);
            }

            @Override
            public String call(String input, ToolContext context) {
                return ToolSelection.ARZT.equals(antwort.tool())
                        || ToolSelection.SIGNAL.equals(antwort.tool())
                        ? antwort.result()
                        : McpWrapper.um(antwort.result());
            }
        };
    }

    private static final String VERTRAG = """
            {"id":4711,"nachname":"Mueller","tarifId":"ATRA_DENT_B","status":"aktiv"}
            """;

    private static final String GOZ = """
            {"tarif":"ATRA_DENT_B","befunde":[
              {"nummer":"2197","status":"ENTHALTEN","leistungsbereich":"ZERH"},
              {"nummer":"2150","status":"ENTHALTEN","leistungsbereich":"ZERH"}]}
            """;

    private static final String ERSTATTUNG = """
            {"erstattungsbetrag":"336.00","rechnungsbetrag":"395.00"}
            """;

    private static final String ARZT = """
            {"plausibilitaet":"plausibel","notwendigkeit":"ueblich",
             "text":"Passt zusammen.","hinweis":"Sprachmodell, kein Beleg."}
            """;
}
