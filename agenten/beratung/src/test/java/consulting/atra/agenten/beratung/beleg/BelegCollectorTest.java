package consulting.atra.agenten.beratung.beleg;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.beratung.tools.BeratungMessages;
import consulting.atra.agenten.beratung.tools.ToolSelection;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.McpWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BelegCollectorTest {

    private final BelegCollector collection = new BelegCollector(JsonMapper.builder().build());

    @Test
    @DisplayName("Fundstellen come from the raw result, with location and links")
    void fundstellenFromTheRawResult() {
        var observed = mit(BelegCollector.BEDINGUNGEN, result(
                hits("bedingungen-balance", "wartezeiten", "§ 4 Wartezeiten")));

        List<Map<String, Object>> belege = collection.collect(observed);

        assertThat(belege).hasSize(1);
        assertThat(belege.getFirst())
                .containsEntry("dokumentId", "bedingungen-balance")
                .containsEntry("abschnittId", "wartezeiten")
                .containsEntry("ueberschrift", "§ 4 Wartezeiten")
                .containsKeys("htmlUrl", "pdfUrl");

        assertThat(belege.getFirst()).doesNotContainKey("text");
    }

    @Test
    @DisplayName("the same passage from two searches appears only once")
    void duplicatesAreDropped() {
        var observed = new ObservedTools(StatusChannel.discarded(), "beratung",
                        ToolSelection.MAPPING, BeratungMessages.ALLE);
        var tool = observed.wrap(List.of(tool(BelegCollector.BEDINGUNGEN, result(
                hits("bedingungen-balance", "wartezeiten", "§ 4 Wartezeiten"))))).getFirst();
        tool.call("{}");
        tool.call("{}");

        assertThat(collection.collect(observed)).hasSize(1);
    }

    @Test
    @DisplayName("the Beratungsleitfaden is no Beleg")
    void theLeitfadenIsNoBeleg() {
        var observed = mit(ConfidentialityGuard.LEITFADEN, """
                {"frage":"zu teuer","vertraulichkeit":"intern","vertraulichkeitshinweis":"intern",
                 "abschnitte":[{"dokumentId":"beratungshandbuch","abschnittId":"einwand",
                                "ueberschrift":"Einwand","text":"…","bewertung":0.9}]}
                """);

        assertThat(collection.collect(observed)).isEmpty();
    }

    @Test
    @DisplayName("without a Bedingungen search the Beleg list is empty - that is valid")
    void anEmptyListIsValid() {
        var withoutCall = new ObservedTools(StatusChannel.discarded(), "beratung",
                        ToolSelection.MAPPING, BeratungMessages.ALLE);
        assertThat(collection.collect(withoutCall)).isEmpty();
    }

    @Test
    @DisplayName("an unreadable result does not cost a finished Auskunft")
    void anUnreadableResultDoesNotThrow() {
        var observed = mit(BelegCollector.BEDINGUNGEN, "das ist kein json");

        assertThat(collection.collect(observed)).isEmpty();
    }


    private static String hits(String dokument, String abschnitt, String ueberschrift) {
        return """
                {"dokumentId":"%s","abschnittId":"%s","ueberschrift":"%s",
                 "text":"Die Wartezeit betraegt acht Monate.","bewertung":0.91,
                 "htmlUrl":"http://localhost:8082/dokumente/%s.html#%s",
                 "pdfUrl":"http://localhost:8082/dokumente/%s.pdf"}
                """.formatted(dokument, abschnitt, ueberschrift, dokument, abschnitt, dokument);
    }

    private static String result(String... treffer) {
        return """
                {"tarif":"ATRA_DENT_B","frage":"Wartezeit","treffer":[%s]}
                """.formatted(String.join(",", treffer));
    }

    private static ObservedTools mit(String name, String result) {
        var observed = new ObservedTools(StatusChannel.discarded(), "beratung",
                        ToolSelection.MAPPING, BeratungMessages.ALLE);
        observed.wrap(List.of(tool(name, result))).getFirst().call("{}");
        return observed;
    }

    private static ToolCallback tool(String name, String result) {
        String onTheWire = McpWrapper.um(result);
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("Test")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
            }

            @Override
            public String call(String input) {
                return onTheWire;
            }

            @Override
            public String call(String input, ToolContext context) {
                return onTheWire;
            }
        };
    }
}
