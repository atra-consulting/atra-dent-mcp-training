package consulting.atra.agenten.schadensfall.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import consulting.atra.agenten.model.ToolSchemaInliner;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BewertungssignalTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    @Test
    @DisplayName("the Vorschlag arrives exactly as the model sent it")
    void theVorschlagIsAdopted() {
        Bewertungssignal signal = new Bewertungssignal();

        call(signal, """
                {"empfehlung":"freigabe","erstattungsvorschlag":"336.00",
                 "positionen":[{"goz":"2197","leistungsbereich":"ZERH","zustand":"ENTHALTEN",
                                "begruendung":"Zahnerhalt, 85 Prozent laut B.2.1"}],
                 "begruendung":"Beide Positionen sind gedeckt."}
                """);

        Bewertungsvorschlag proposal = signal.handedOver().orElseThrow();
        assertThat(proposal.empfehlung()).isEqualTo(Bewertungsvorschlag.FREIGABE);
        assertThat(proposal.erstattungsvorschlag()).isEqualByComparingTo(new BigDecimal("336.00"));
        assertThat(proposal.positionen()).hasSize(1);
        assertThat(proposal.positionen().getFirst().goz()).isEqualTo("2197");
        assertThat(proposal.positionen().getFirst().leistungsbereich()).isEqualTo("ZERH");
        assertThat(proposal.begruendung()).contains("gedeckt");
    }

    @Test
    @DisplayName("without a call there is no Vorschlag")
    void withoutACallNothing() {
        assertThat(new Bewertungssignal().handedOver()).isEmpty();
    }

    @Test
    @DisplayName("the first call wins")
    void theFirstCallWins() {
        Bewertungssignal signal = new Bewertungssignal();

        call(signal, """
                {"empfehlung":"eskalation","positionen":[],"begruendung":"Erst so."}
                """);
        call(signal, """
                {"empfehlung":"freigabe","erstattungsvorschlag":"336.00","positionen":[],
                 "begruendung":"Dann doch anders."}
                """);

        Bewertungsvorschlag proposal = signal.handedOver().orElseThrow();
        assertThat(proposal.empfehlung()).isEqualTo(Bewertungsvorschlag.ESKALATION);
        assertThat(proposal.begruendung()).isEqualTo("Erst so.");
    }

    @Test
    @DisplayName("the Empfehlung is normalized: \"  ESKALATION  \" is eskalation")
    void theEmpfehlungIsNormalized() {
        Bewertungssignal signal = new Bewertungssignal();

        call(signal, """
                {"empfehlung":"  ESKALATION  ","positionen":[],"begruendung":"Da stimmt etwas nicht."}
                """);

        Bewertungsvorschlag proposal = signal.handedOver().orElseThrow();
        assertThat(proposal.empfehlung()).isEqualTo(Bewertungsvorschlag.ESKALATION);
        assertThat(new ApprovalGuard(new BigDecimal("1500.00"))
                .check(null, proposal, RawFindings.empty()).freigabe()).isFalse();
    }

    @Test
    @DisplayName("an unknown Empfehlung is rejected and not stored")
    void anUnknownEmpfehlungIsRejected() {
        Bewertungssignal signal = new Bewertungssignal();

        assertThatThrownBy(() -> call(signal, """
                {"empfehlung":"vielleicht","positionen":[],"begruendung":"Unentschieden."}
                """))
                .hasMessageContaining(Bewertungsvorschlag.FREIGABE)
                .hasMessageContaining(Bewertungsvorschlag.ESKALATION);

        assertThat(signal.handedOver()).isEmpty();
    }

    @Test
    @DisplayName("an unreadable Betrag is no Betrag and does not throw")
    void anUnreadableBetragStaysEmpty() {
        Bewertungssignal signal = new Bewertungssignal();

        call(signal, """
                {"empfehlung":"freigabe","erstattungsvorschlag":"etwa 336 Euro","positionen":[],
                 "begruendung":"Ungefaehr."}
                """);

        assertThat(signal.handedOver().orElseThrow().erstattungsvorschlag()).isNull();
    }

    @Test
    @DisplayName("the tool is named bewertung_abgeben and is the only one")
    void theToolIsNamedAsAgreed() {
        List<ToolCallback> callbacks = new Bewertungssignal().callbacks();

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.getFirst().getToolDefinition().name()).isEqualTo(ToolSelection.SIGNAL);
    }

    @Test
    @DisplayName("the schema reaches the provider without $defs and with all four fields")
    void theSchemaArrivesAtTheProviderWithoutDefs() {
        ToolCallback resolved = ToolSchemaInliner.resolve(
                new Bewertungssignal().callbacks()).getFirst();
        String schema = resolved.getToolDefinition().inputSchema();

        assertThat(schema).doesNotContain("$defs").doesNotContain("$ref");

        JsonNode positionen = IMAGES.readTree(schema)
                .get("properties").get("positionen").get("items").get("properties");
        assertThat(positionen.propertyNames())
                .containsExactlyInAnyOrder("index", "goz", "leistungsbereich", "zustand",
                        "begruendung");
        positionen.properties().forEach(field ->
                assertThat(field.getValue().get("description").asString())
                        .as("Beschreibung von %s", field.getKey())
                        .isNotBlank());
        assertThat(positionen.get("goz").get("description").asString())
                .contains("Gebuehrennummer");

        assertThat(positionen.get("index").get("description").asString())
                .contains("positionen").contains("null");
    }

    @Test
    @DisplayName("a Position without a Gebuehrennummer arrives with its index")
    void aPositionWithoutANummerComesWithAnIndex() {
        Bewertungssignal signal = new Bewertungssignal();

        call(signal, """
                {"empfehlung":"freigabe","erstattungsvorschlag":"336.00",
                 "positionen":[{"index":3,"leistungsbereich":"ZE","zustand":"NICHT_BESTIMMBAR",
                                "begruendung":"Laborkosten zur Krone an Zahn 46."}],
                 "begruendung":"Das Labor folgt der Krone."}
                """);

        Bewertungsvorschlag.Position position =
                signal.handedOver().orElseThrow().positionen().getFirst();
        assertThat(position.index()).isEqualTo(3);
        assertThat(position.goz()).isNull();
        assertThat(position.leistungsbereich()).isEqualTo("ZE");
    }

    private static void call(Bewertungssignal signal, String arguments) {
        signal.callbacks().getFirst().call(arguments);
    }
}
