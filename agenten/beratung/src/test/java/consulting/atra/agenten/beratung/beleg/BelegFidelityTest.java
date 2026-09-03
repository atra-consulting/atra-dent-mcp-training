package consulting.atra.agenten.beratung.beleg;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BelegFidelityTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final BelegFidelity belegFidelity = new BelegFidelity(MAPPER);

    private final List<Map<String, String>> reported = new ArrayList<>();

    private final Einreichtool einreichen = new Einreichtool();

    private List<ToolCallback> wrapped(String gesamtbetrag) {
        return belegFidelity.wrap(List.of(einreichen), gesamtbetrag, (vomModell, ausDemBeleg) -> {
            Map<String, String> paar = new LinkedHashMap<>();
            paar.put("modell", vomModell);
            paar.put("beleg", ausDemBeleg);
            reported.add(paar);
        });
    }

    private String totalAmountAtKernsystem() {
        return MAPPER.readTree(einreichen.input).path("rechnung").path("gesamtbetrag").asString();
    }

    @Test
    @DisplayName("the model's Gesamtbetrag is replaced by the one from the extraction")
    void theBetragFromTheModelIsReplaced() {
        wrapped("1746.28").getFirst().call("""
                {"behandlungsdatum":"2026-06-23","positionen":[{"betrag":"954.98"}],\
                "rechnung":{"rechnungsnummer":"KKW-2026-003288","gesamtbetrag":"954.98"}}""");

        assertThat(totalAmountAtKernsystem()).isEqualTo("1746.28");
        assertThat(einreichen.input).contains("KKW-2026-003288").contains("954.98");
        assertThat(reported).singleElement()
                .isEqualTo(Map.of("modell", "954.98", "beleg", "1746.28"));
    }

    @Test
    @DisplayName("if the rechnung block is missing it is created with the Betrag from the extraction")
    void withoutARechnungBlockOneIsCreated() {
        wrapped("1746.28").getFirst().call("""
                {"behandlungsdatum":"2026-06-23","positionen":[{"betrag":"954.98"}]}""");

        assertThat(totalAmountAtKernsystem()).isEqualTo("1746.28");
        Map<String, String> erwartet = new LinkedHashMap<>();
        erwartet.put("modell", null);
        erwartet.put("beleg", "1746.28");
        assertThat(reported).containsExactly(erwartet);
    }

    @Test
    @DisplayName("if the Betrag matches, the input passes through unchanged")
    void theSameBetragStaysUntouched() {
        String input = """
                {"positionen":[{"betrag":"1746.28"}],"rechnung":{"gesamtbetrag":"1746.28"}}""";

        wrapped("1746.28").getFirst().call(input);

        assertThat(einreichen.input).isEqualTo(input);
        assertThat(reported).isEmpty();
    }

    @Test
    @DisplayName("the value is compared, not the spelling")
    void theSameValueInAnotherSpelling() {
        String input = """
                {"rechnung":{"gesamtbetrag":"420.0"}}""";

        wrapped("420.00").getFirst().call(input);

        assertThat(einreichen.input).isEqualTo(input);
        assertThat(reported).isEmpty();
    }

    @Test
    @DisplayName("the correction happens with a ToolContext too -- that is the path Spring AI takes")
    void thePathIsCorrectedWithTheToolContext() {
        ToolContext context = new ToolContext(Map.of("egal", "wert"));

        wrapped("1746.28").getFirst().call("""
                {"positionen":[{"betrag":"954.98"}],"rechnung":{"gesamtbetrag":"954.98"}}""",
                context);

        assertThat(totalAmountAtKernsystem()).isEqualTo("1746.28");
        assertThat(einreichen.context).isSameAs(context);
    }

    @Test
    @DisplayName("a throwing channel discards no correction that was already applied")
    void aThrowingChannelDiscardsNoCorrection() {
        List<ToolCallback> wrapped = belegFidelity.wrap(List.of(einreichen), "1746.28",
                (vomModell, ausDemBeleg) -> {
                    throw new IllegalStateException("Kanal weg");
                });

        assertThatThrownBy(() -> wrapped.getFirst().call("""
                {"rechnung":{"gesamtbetrag":"954.98"}}"""))
                .isInstanceOf(IllegalStateException.class);

        assertThat(einreichen.input).isNull();
    }

    @Test
    @DisplayName("without a Beleg the tool list stays the same")
    void withoutABelegNoWrapper() {
        List<ToolCallback> tools = List.of(einreichen);

        assertThat(belegFidelity.wrap(tools, null, (a, b) -> { })).isSameAs(tools);
    }

    @Test
    @DisplayName("another tool is not touched")
    void anotherToolStaysUnwrapped() {
        Einreichtool fremd = new Einreichtool("mein_vertrag_lesen");

        List<ToolCallback> wrapped =
                belegFidelity.wrap(List.of(fremd), "1746.28", (a, b) -> { });

        assertThat(wrapped.getFirst()).isSameAs(fremd);
    }

    @Test
    @DisplayName("unreadable arguments pass through unchanged -- the Einreichung must not fail on the "
            + "envelope")
    void unreadableArgumentsPassThrough() {
        wrapped("1746.28").getFirst().call("das ist kein JSON");

        assertThat(einreichen.input).isEqualTo("das ist kein JSON");
        assertThat(reported).isEmpty();
    }

    @Test
    @DisplayName("the Gesamtbetrag comes from the data part, as a decimal string as well as a number")
    void gesamtbetragFromTheDataPart() {
        assertThat(BelegFidelity.totalAmount(Map.of("gesamtbetrag", "1746.28"))).isEqualTo("1746.28");
        assertThat(BelegFidelity.totalAmount(Map.of("gesamtbetrag", 1746.28))).isEqualTo("1746.28");
    }

    @Test
    @DisplayName("what the Kernsystem would reject anyway is not written")
    void anUnusableBetragYieldsNothing() {
        assertThat(BelegFidelity.totalAmount(Map.of("gesamtbetrag", "1.746,28 EUR"))).isNull();
        assertThat(BelegFidelity.totalAmount(Map.of("rechnungsnummer", "2026-4711"))).isNull();
        assertThat(BelegFidelity.totalAmount("kein Beleg")).isNull();
        assertThat(BelegFidelity.totalAmount(null)).isNull();
    }

    private static final class Einreichtool implements ToolCallback {

        private final String name;

        private String input;

        private ToolContext context;

        Einreichtool() {
            this(BelegFidelity.EINREICHEN);
        }

        Einreichtool(String name) {
            this.name = name;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(name).description("Test")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
        }

        @Override
        public String call(String input) {
            this.input = input;
            return "{}";
        }

        @Override
        public String call(String input, ToolContext context) {
            this.context = context;
            return call(input);
        }
    }
}
