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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RawFindingsTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    @Test
    @DisplayName("without a single call everything is empty - and that is valid")
    void withoutCallsEverythingIsEmpty() {
        RawFindings findings = RawFindings.aus(observed(Map.of()), IMAGES);

        assertThat(findings.vertrag()).isEmpty();
        assertThat(findings.gozBefunde()).isEmpty();
        assertThat(findings.erstattungsbetrag()).isEmpty();
        assertThat(findings.arztauskunft()).isEmpty();
        assertThat(findings.arztAufgerufen()).isFalse();
        assertThat(findings.arztGestoert()).isFalse();
        assertThat(findings.andereFaelle()).isEmpty();
        assertThat(findings.schadensfaelleGelesen()).isFalse();
    }

    @Test
    @DisplayName("an empty history counts as read - unlike no call at all")
    void anEmptyHistoryCountsAsRead() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.SCHADENSFAELLE, List.of("[]"))), IMAGES);

        assertThat(findings.andereFaelle()).isEmpty();
        assertThat(findings.schadensfaelleGelesen()).isTrue();
    }

    @Test
    @DisplayName("a response without a Schadensfall list does not count as read")
    void anOffShapeHistoryIsNotRead() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.SCHADENSFAELLE, List.of("{\"fehler\":\"Kernsystem antwortet nicht\"}"))),
                IMAGES);

        assertThat(findings.andereFaelle()).isEmpty();
        assertThat(findings.schadensfaelleGelesen()).isFalse();
    }

    @Test
    @DisplayName("the Vertrag comes from mein_vertrag_lesen")
    void theVertragFromTheVertragTool() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.VERTRAG, List.of(VERTRAG))), IMAGES);

        assertThat(findings.vertrag()).isPresent();
        assertThat(findings.vertrag().orElseThrow().get("nachname").asString()).isEqualTo("Mueller");
    }

    @Test
    @DisplayName("on the second Vertrag call the last result counts")
    void theLastVertragWins() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.VERTRAG, List.of(VERTRAG, VERTRAG.replace("Mueller", "Schmidt")))),
                IMAGES);

        assertThat(findings.vertrag().orElseThrow().get("nachname").asString()).isEqualTo("Schmidt");
    }

    @Test
    @DisplayName("the GOZ Befunde of all calls appear in sequence")
    void gozFindingsOfAllCalls() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.GOZ, List.of(
                        gozResult(finding("2197", "ENTHALTEN", null)),
                        gozResult(finding("9010", "NICHT_ENTHALTEN", null))))),
                IMAGES);

        assertThat(findings.gozBefunde()).hasSize(2);
        assertThat(findings.gozBefunde().getFirst().get("nummer").asString()).isEqualTo("2197");
        assertThat(findings.gozBefunde().getLast().get("status").asString()).isEqualTo("NICHT_ENTHALTEN");
    }

    @Test
    @DisplayName("the Erstattungsbetrag is a BigDecimal from the last Rechenkern result")
    void erstattungsbetragAsBigDecimal() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ERSTATTUNG, List.of(
                        erstattung("100.00"),
                        erstattung("336.00")))),
                IMAGES);

        assertThat(findings.erstattungsbetrag()).isPresent();
        assertThat(findings.erstattungsbetrag().orElseThrow())
                .isEqualByComparingTo(new BigDecimal("336.00"));
    }

    @Test
    @DisplayName("the arguments of erstattung_berechnen come from the last call")
    void erstattungArgumentsFromTheLastCall() {
        RawFindings findings = RawFindings.aus(withArguments(ToolSelection.ERSTATTUNG, List.of(
                "{\"tarifId\":\"ATRA_DENT_A\"}",
                "{\"tarifId\":\"ATRA_DENT_B\",\"behandlungsdatum\":\"2026-05-03\"}")), IMAGES);

        assertThat(findings.erstattungsargumente()).isPresent();
        assertThat(findings.erstattungsargumente().orElseThrow().get("tarifId").asString())
                .isEqualTo("ATRA_DENT_B");
    }

    @Test
    @DisplayName("the arguments of all goz_pruefen calls appear side by side")
    void gozArgumentsOfAllCalls() {
        RawFindings findings = RawFindings.aus(withArguments(ToolSelection.GOZ, List.of(
                "{\"tarif\":\"ATRA_DENT_B\",\"nummern\":[\"2197\"]}",
                "{\"tarif\":\"ATRA_DENT_A\",\"nummern\":[\"2200\"]}")), IMAGES);

        assertThat(findings.gozArgumente()).hasSize(2);
        assertThat(findings.gozArgumente().getLast().get("tarif").asString())
                .isEqualTo("ATRA_DENT_A");
    }

    @Test
    @DisplayName("unreadable arguments drop out and do not throw")
    void unreadableArgumentsDoNotThrow() {
        RawFindings findings = RawFindings.aus(
                withArguments(ToolSelection.GOZ, List.of("das ist kein json")), IMAGES);

        assertThat(findings.gozArgumente()).isEmpty();
    }

    @Test
    @DisplayName("without a call there are no arguments")
    void withoutACallNoArguments() {
        RawFindings findings = RawFindings.aus(observed(Map.of()), IMAGES);

        assertThat(findings.gozArgumente()).isEmpty();
        assertThat(findings.erstattungsargumente()).isEmpty();
    }

    @Test
    @DisplayName("a Rechenkern result without a Betrag is no Betrag")
    void erstattungWithoutABetrag() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ERSTATTUNG, List.of("{\"rechnungsbetrag\":\"395.00\"}"))), IMAGES);

        assertThat(findings.erstattungsbetrag()).isEmpty();
    }

    @Test
    @DisplayName("the Arztauskunft comes from the data part of the Arzt tool")
    void arztauskunftFromTheArztTool() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ARZT, List.of(ARZT))), IMAGES);

        assertThat(findings.arztAufgerufen()).isTrue();
        assertThat(findings.arztGestoert()).isFalse();
        assertThat(findings.arztauskunft()).isPresent();
        assertThat(findings.arztauskunft().orElseThrow().get("plausibilitaet").asString())
                .isEqualTo("plausibel");
    }

    @Test
    @DisplayName("an Arzt result with fehler is a failed call, not an Auskunft")
    void anArztErrorIsAFailure() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ARZT, List.of("{\"fehler\":\"Arztservice nicht erreichbar\"}"))),
                IMAGES);

        assertThat(findings.arztAufgerufen()).isTrue();
        assertThat(findings.arztGestoert()).isTrue();
        assertThat(findings.arztauskunft()).isEmpty();
    }

    @Test
    @DisplayName("an unreadable Arzt result counts as failed too")
    void anUnreadableArztAnswerIsAFailure() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ARZT, List.of("das ist kein json"))), IMAGES);

        assertThat(findings.arztGestoert()).isTrue();
        assertThat(findings.arztauskunft()).isEmpty();
    }

    @Test
    @DisplayName("the other Schadensfaelle come as a list from meine_schadensfaelle_auflisten")
    void otherSchadensfaelleAsAList() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.SCHADENSFAELLE, List.of("""
                        [{"id":50014,"behandlungsdatum":"2026-05-03","rechnungsbetrag":"395.00"},
                         {"id":50015,"behandlungsdatum":"2026-06-01","rechnungsbetrag":"120.00"}]
                        """))), IMAGES);

        assertThat(findings.andereFaelle()).hasSize(2);
        assertThat(findings.andereFaelle().getFirst().get("id").asLong()).isEqualTo(50014L);
    }

    @Test
    @DisplayName("a wrapper with the field schadensfaelle is read the same way")
    void otherSchadensfaelleFromTheWrapperField() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.SCHADENSFAELLE, List.of(
                        "{\"schadensfaelle\":[{\"id\":50014}]}"))), IMAGES);

        assertThat(findings.andereFaelle()).hasSize(1);
    }

    @Test
    @DisplayName("the general Tarif Wartezeit comes from tarife_vergleichen, the row of the caller's own Tarif")
    void theTarifWartezeitFromTheComparison() {
        RawFindings findings = RawFindings.aus(observed(new LinkedHashMap<>(Map.of(
                ToolSelection.VERTRAG, List.of(VERTRAG),
                ToolSelection.COMPARISON, List.of(comparison())))), IMAGES);

        assertThat(findings.allgemeineWartezeitMonate()).contains(8);
        assertThat(findings.wartezeitEntfaelltBeiVorversicherung()).isTrue();
    }

    @Test
    @DisplayName("if the caller's own Tarif is not in the table, there is no Wartezeit")
    void theTarifIsNotInTheTable() {
        RawFindings findings = RawFindings.aus(observed(new LinkedHashMap<>(Map.of(
                ToolSelection.VERTRAG, List.of(VERTRAG.replace("ATRA_DENT_B", "ATRA_DENT_Z")),
                ToolSelection.COMPARISON, List.of(comparison())))), IMAGES);

        assertThat(findings.allgemeineWartezeitMonate()).isEmpty();
        assertThat(findings.wartezeitEntfaelltBeiVorversicherung()).isFalse();
    }

    @Test
    @DisplayName("without a Vertrag no column is guessed")
    void withoutAVertragNoColumn() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.COMPARISON, List.of(comparison()))), IMAGES);

        assertThat(findings.allgemeineWartezeitMonate()).isEmpty();
    }

    @Test
    @DisplayName("a well-formed but off-shape Rechenkern result does not throw")
    void anOffShapeErstattungsbetrag() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ERSTATTUNG, List.of("{\"erstattungsbetrag\":{\"wert\":\"336.00\"}}"))),
                IMAGES);

        assertThat(findings.erstattungsbetrag()).isEmpty();
    }

    @Test
    @DisplayName("an off-shape Arztauskunft does not throw and does not count as failed")
    void anOffShapeArztauskunft() {
        RawFindings findings = RawFindings.aus(observed(Map.of(
                ToolSelection.ARZT, List.of(
                        "{\"plausibilitaet\":{\"wert\":\"auffaellig\"},\"notwendigkeit\":[\"fraglich\"]}"))),
                IMAGES);

        assertThat(findings.arztAufgerufen()).isTrue();
        assertThat(findings.arztGestoert()).isFalse();
        assertThat(findings.arztauskunft()).isPresent();
    }

    @Test
    @DisplayName("an off-shape comparison result does not throw")
    void anOffShapeComparison() {
        RawFindings findings = RawFindings.aus(observed(new LinkedHashMap<>(Map.of(
                ToolSelection.VERTRAG, List.of(VERTRAG),
                ToolSelection.COMPARISON, List.of(
                        "{\"tarife\":{\"ATRA_DENT_B\":{\"wartezeitMonate\":8}}}")))), IMAGES);

        assertThat(findings.allgemeineWartezeitMonate()).isEmpty();
    }

    @Test
    @DisplayName("an unreadable result does not cost the remaining Befunde")
    void anUnreadableResultDoesNotThrow() {
        RawFindings findings = RawFindings.aus(observed(new LinkedHashMap<>(Map.of(
                ToolSelection.VERTRAG, List.of("<html>Fehlerseite</html>"),
                ToolSelection.ERSTATTUNG, List.of(erstattung("336.00"))))), IMAGES);

        assertThat(findings.vertrag()).isEmpty();
        assertThat(findings.erstattungsbetrag()).isPresent();
    }


    static final String VERTRAG = """
            {"id":4711,"vorname":"Anna","nachname":"Mueller","geburtsdatum":"1990-04-12",
             "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01","status":"aktiv"}
            """;

    static String comparison() {
        return """
                {"stand":"2026-01-01","tarife":[
                  {"schluessel":"ATRA_DENT_A","anzeigename":"atra.dent.aktiv","wartezeitMonate":3,
                   "wartezeitEntfaelltBeiVorversicherung":false,"zahnstaffel":[]},
                  {"schluessel":"ATRA_DENT_B","anzeigename":"atra.dent.balance","wartezeitMonate":8,
                   "wartezeitEntfaelltBeiVorversicherung":true,"zahnstaffel":[]}],
                 "leistungsbereiche":[]}
                """;
    }

    static final String ARZT = """
            {"plausibilitaet":"plausibel","notwendigkeit":"ueblich","positionen":[],
             "text":"Die Positionen passen zueinander.","quelle":"Arztservice",
             "modell":"gemini-3.6-flash","hinweis":"Sprachmodell, keine Entscheidung."}
            """;

    static String finding(String number, String status, String leistungsbereich) {
        return """
                {"nummer":"%s","bezeichnung":"Leistung","abschnitt":"K",
                 "leistungsbereich":%s,"status":"%s","quote":85,
                 "begruendung":"Begruendung"}
                """.formatted(number,
                leistungsbereich == null ? "null" : "\"" + leistungsbereich + "\"", status);
    }

    static String gozResult(String... befunde) {
        return "{\"tarif\":\"ATRA_DENT_B\",\"befunde\":[" + String.join(",", befunde) + "]}";
    }

    static String erstattung(String betrag) {
        return """
                {"erstattungsbetrag":"%s","rechnungsbetrag":"395.00","eigenanteil":"59.00",
                 "versicherungsjahr":2,"schritte":[]}
                """.formatted(betrag);
    }

    private static ObservedTools observed(Map<String, List<String>> results) {
        var observed = new ObservedTools(StatusChannel.discarded(), "schadensfall",
                _ -> null, _ -> null);
        List<ToolCallback> tools = new ArrayList<>();
        results.forEach((name, respond) -> tools.add(tool(name, respond)));
        for (ToolCallback wrapped : observed.wrap(tools)) {
            int calls = results.get(wrapped.getToolDefinition().name()).size();
            for (int i = 0; i < calls; i++) {
                wrapped.call("{}");
            }
        }
        return observed;
    }

    private static ObservedTools withArguments(String name, List<String> arguments) {
        var observed = new ObservedTools(StatusChannel.discarded(), "schadensfall",
                _ -> null, _ -> null);
        ToolCallback wrapped =
                observed.wrap(List.of(tool(name, List.of("{}")))).getFirst();
        arguments.forEach(wrapped::call);
        return observed;
    }

    private static ToolCallback tool(String name, List<String> respond) {
        List<String> onTheWire = respond.stream().map(McpWrapper::um).toList();
        int[] naechste = {0};
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("Test")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
            }

            @Override
            public String call(String input) {
                return call(input, null);
            }

            @Override
            public String call(String input, ToolContext context) {
                return onTheWire.get(Math.min(naechste[0]++, onTheWire.size() - 1));
            }
        };
    }
}
