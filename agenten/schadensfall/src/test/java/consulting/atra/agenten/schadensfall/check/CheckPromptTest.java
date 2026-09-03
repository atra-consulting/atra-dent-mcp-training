package consulting.atra.agenten.schadensfall.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class CheckPromptTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final String FALL = """
            {"id":50071,"kundenId":4711,"behandlungsdatum":"2026-05-03",
             "positionen":[
               {"goz":"2197","zahn":"46","betrag":"200.00","beschreibung":"Krone"},
               {"goz":null,"zahn":"46","betrag":"486.30","beschreibung":"Laborkosten"}],
             "rechnungsbetrag":"686.30"}
            """;

    @Test
    @DisplayName("every Position carries its index, zero-based and in order")
    void positionenCarryTheirIndex() {
        JsonNode read = IMAGES.readTree(withoutHeading(CheckPrompt.userText(node(FALL))));

        assertThat(read.get("positionen").get(0).get("index").asInt()).isZero();
        assertThat(read.get("positionen").get(1).get("index").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("otherwise the Schadensfall stays word for word the Akte of the Kernsystem")
    void otherwiseUnchanged() {
        JsonNode read = IMAGES.readTree(withoutHeading(CheckPrompt.userText(node(FALL))));

        JsonNode withoutIndex = read.deepCopy();
        withoutIndex.get("positionen").forEach(position ->
                ((tools.jackson.databind.node.ObjectNode) position).remove("index"));
        assertThat(withoutIndex).isEqualTo(node(FALL));
    }

    @Test
    @DisplayName("a Schadensfall without Positionen and an off-shape one do not throw")
    void offShapeInputDoesNotThrow() {
        assertThatNoException().isThrownBy(() -> CheckPrompt.userText(null));
        assertThatNoException().isThrownBy(() ->
                CheckPrompt.userText(node("{\"id\":50071}")));
        assertThatNoException().isThrownBy(() ->
                CheckPrompt.userText(node("{\"positionen\":\"keine Liste\"}")));
        assertThatNoException().isThrownBy(() ->
                CheckPrompt.userText(node("{\"positionen\":[\"kein Objekt\"]}")));
    }

    @Test
    @DisplayName("the role names the index and says Positionen without a Nummer come along")
    void theRoleNamesIndexAndPositionenWithoutANummer() {
        assertThat(CheckPrompt.SYSTEM)
                .contains("index")
                .contains("Material-, Labor- oder Verlangensleistung")
                .contains("Der Zahn geht dem Datum");
    }

    private static String withoutHeading(String nutzertext) {
        return nutzertext.substring(CheckPrompt.CASE_BLOCK.length());
    }

    private static JsonNode node(String json) {
        return IMAGES.readTree(json);
    }
}
