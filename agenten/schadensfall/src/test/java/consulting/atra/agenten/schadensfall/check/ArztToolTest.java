package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.a2a.client.AgentClient;
import consulting.atra.agenten.a2a.client.AgentUnreachableException;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArztToolTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final String FALL = """
            {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-01-08",
             "positionen":[
               {"goz":"2197","betrag":"25.00","beschreibung":"Adhaesive Befestigung"},
               {"goz":"2150","betrag":"370.00","beschreibung":"Aufbaufuellung","zahn":"36","anzahl":2,
                "datum":"2026-01-08"}],
             "rechnungsbetrag":"395.00","status":"in_pruefung"}
            """;

    @Test
    @DisplayName("the Positionen of the Schadensfall go to the Arztservice in structured form")
    void positionenFromTheSchadensfall() {
        Aufzeichnung recording = new Aufzeichnung();
        ArztTool tool = new ArztTool(recording, IMAGES, fall(), StatusChannel.discarded());

        tool.call("{}");

        Map<String, Object> data = recording.data;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positionen = (List<Map<String, Object>>) data.get("positionen");
        assertThat(positionen).hasSize(2);
        assertThat(positionen.getFirst())
                .containsEntry("gozNummer", "2197")
                .containsEntry("bezeichnung", "Adhaesive Befestigung");
        assertThat(positionen.get(1))
                .containsEntry("zahn", "36")
                .containsEntry("anzahl", 2)
                .containsEntry("datum", "2026-01-08");
        assertThat(data).containsEntry("behandlungsdatum", "2026-01-08");
        assertThat(recording.kundenId).isNull();
        assertThat(recording.text).isNotBlank();
    }

    @Test
    @DisplayName("the data part of the response comes back as JSON")
    void theAuskunftAsJson() {
        Aufzeichnung recording = new Aufzeichnung();
        recording.antwort = new SubagentResponse(Outcome.COMPLETED, "Prosa", "k1", "t1",
                Map.of("plausibilitaet", "plausibel", "notwendigkeit", "ueblich",
                        "text", "Passt zusammen.", "hinweis", "Sprachmodell, kein Beleg."));
        ArztTool tool = new ArztTool(recording, IMAGES, fall(), StatusChannel.discarded());

        JsonNode info = IMAGES.readTree(tool.call("{}"));

        assertThat(info.get("plausibilitaet").asString()).isEqualTo("plausibel");
        assertThat(info.get("hinweis").asString()).contains("Sprachmodell");
    }

    @Test
    @DisplayName("an outage becomes the result and not an exception")
    void anOutageBecomesTheResult() {
        Aufzeichnung recording = new Aufzeichnung();
        recording.failure = new AgentUnreachableException("Arztservice", "Kein Netz");
        ArztTool tool = new ArztTool(recording, IMAGES, fall(), StatusChannel.discarded());

        JsonNode result = IMAGES.readTree(tool.call("{}"));

        assertThat(result.get(ArztTool.ERROR)).isNotNull();
        assertThat(result.get(ArztTool.ERROR).asString()).contains("nicht erreichbar");
    }

    @Test
    @DisplayName("an input-required from the Arztservice is no Auskunft")
    void anInputRequiredIsNoAuskunft() {
        Aufzeichnung recording = new Aufzeichnung();
        recording.antwort = new SubagentResponse(Outcome.INPUT_REQUIRED,
                "Welche Positionen soll ich beurteilen?", "k1", "t1");
        ArztTool tool = new ArztTool(recording, IMAGES, fall(), StatusChannel.discarded());

        JsonNode result = IMAGES.readTree(tool.call("{}"));

        assertThat(result.get(ArztTool.ERROR)).isNotNull();
    }

    @Test
    @DisplayName("a field the Akte does not carry stays out instead of becoming null")
    void missingFieldsStayOut() {
        Aufzeichnung recording = new Aufzeichnung();
        ArztTool tool = new ArztTool(recording, IMAGES, IMAGES.readTree("""
                {"id":50071,"kundenId":4711,
                 "positionen":[{"betrag":"25.00"}]}
                """), StatusChannel.discarded());

        tool.call("{}");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positionen =
                (List<Map<String, Object>>) recording.data.get("positionen");
        assertThat(positionen.getFirst())
                .doesNotContainKey("gozNummer")
                .doesNotContainKey("bezeichnung")
                .doesNotContainKey("datum");
    }

    @Test
    @DisplayName("two identical Nummern on the same Zahn travel with their own dates")
    void aDuplicatePositionCarriesItsDatum() {
        Aufzeichnung recording = new Aufzeichnung();
        ArztTool tool = new ArztTool(recording, IMAGES, IMAGES.readTree("""
                {"id":50099,"kundenId":4711,"behandlungsdatum":"2026-06-08",
                 "positionen":[
                   {"goz":"9010","betrag":"900.00","beschreibung":"Implantatinsertion",
                    "zahn":"36","datum":"2026-06-08"},
                   {"goz":"9010","betrag":"900.00","beschreibung":"Implantatinsertion",
                    "zahn":"36","datum":"2026-07-08"}]}
                """), StatusChannel.discarded());

        tool.call("{}");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> positionen =
                (List<Map<String, Object>>) recording.data.get("positionen");
        assertThat(positionen).hasSize(2);
        assertThat(positionen.getFirst()).containsEntry("datum", "2026-06-08");
        assertThat(positionen.get(1)).containsEntry("datum", "2026-07-08");
    }

    @Test
    @DisplayName("a Schadensfall without Positionen still asks instead of staying silent")
    void withoutFullTextPositionen() {
        Aufzeichnung recording = new Aufzeichnung();
        ArztTool tool = new ArztTool(recording, IMAGES,
                IMAGES.readTree("{\"id\":50071,\"kundenId\":4711}"), StatusChannel.discarded());

        tool.call("{}");

        assertThat(recording.data.get("positionen")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    @DisplayName("the tool is named arzt_befragen and states its caveat")
    void theToolIsNamedAsAgreed() {
        ArztTool tool = new ArztTool(new Aufzeichnung(), IMAGES, fall(),
                StatusChannel.discarded());

        assertThat(tool.getToolDefinition().name()).isEqualTo(ToolSelection.ARZT);
        assertThat(tool.getToolDefinition().description())
                .contains("Sprachmodell")
                .contains("kein Beleg");
    }

    private static JsonNode fall() {
        return IMAGES.readTree(FALL);
    }

    private static final class Aufzeichnung implements AgentClient {

        private final List<String> calls = new ArrayList<>();
        private Map<String, Object> data = new LinkedHashMap<>();
        private String text;
        private Long kundenId;
        private RuntimeException failure;
        private SubagentResponse antwort = new SubagentResponse(Outcome.COMPLETED, "Prosa", "k1", "t1",
                Map.of("plausibilitaet", "plausibel", "notwendigkeit", "ueblich",
                        "text", "Passt zusammen.", "hinweis", "Sprachmodell, kein Beleg."));

        @Override
        public SubagentResponse send(String contextId, String taskId, String text,
                                       Map<String, Object> data, Long kundenId,
                                       StatusChannel status) {
            this.calls.add(text);
            this.text = text;
            this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
            this.kundenId = kundenId;
            if (failure != null) {
                throw failure;
            }
            return antwort;
        }
    }
}
