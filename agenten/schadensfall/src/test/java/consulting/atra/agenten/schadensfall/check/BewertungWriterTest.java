package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.MandantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BewertungWriterTest {

    private static final int POSITIONEN_IM_FALL = 5;

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private final BewertungWriter writer = new BewertungWriter(IMAGES);

    @Test
    @DisplayName("the arguments have exactly the shape schadensfall_bewerten accepts")
    void theArgumentShapeIsRight() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, freigabe(), POSITIONEN_IM_FALL,
                List.of(Map.of("zeitpunkt", "2026-08-18T10:00:00Z", "akteur", "agent",
                        "schritt", "GOZ-Pruefung")),
                "gemini-3.6-flash", tool);

        JsonNode arguments = IMAGES.readTree(tool.arguments);
        assertThat(arguments.get("schadensfallId").asLong()).isEqualTo(50071L);
        JsonNode bewertung = arguments.get("bewertung");
        assertThat(bewertung.get("empfehlung").asString()).isEqualTo("freigabe");
        assertThat(bewertung.get("erstattungsvorschlag").asString()).isEqualTo("336.00");
        assertThat(bewertung.get("agent").asString()).isEqualTo("schadensfall");
        assertThat(bewertung.get("modell").asString()).isEqualTo("gemini-3.6-flash");
        assertThat(bewertung.get("begruendung").asString()).contains("gedeckt");
        assertThat(bewertung.get("positionen").get(0).get("goz").asString()).isEqualTo("2197");
        assertThat(bewertung.get("positionen").get(0).get("zustand").asString())
                .isEqualTo("ENTHALTEN");
        assertThat(bewertung.get("arztauskunft").get("plausibilitaet").asString())
                .isEqualTo("plausibel");
        assertThat(arguments.get("protokoll").get(0).get("schritt").asString())
                .isEqualTo("GOZ-Pruefung");
    }

    @Test
    @DisplayName("the write happens under the Kundennummer of the Schadensfall")
    void underTheKundennummerOfTheSchadensfall() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, freigabe(), POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(tool.kundenId).isEqualTo(4711L);
    }

    @Test
    @DisplayName("a Eskalation carries its reasons and no Betrag")
    void eskalationWithReasons() {
        Schreibtool tool = new Schreibtool();
        BewertungResult eskalation = new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                List.of(new Eskalationsgrund(Eskalationsgrund.GOZ_UNCLEAR, "Zu 9010 fehlt ein Befund.")),
                List.of(), null, "Bitte ansehen.");

        writer.write(50071L, 4711L, eskalation, POSITIONEN_IM_FALL, List.of(), "modell", tool);

        JsonNode bewertung = IMAGES.readTree(tool.arguments).get("bewertung");
        assertThat(bewertung.get("eskalationsgruende").get(0).get("code").asString())
                .isEqualTo(Eskalationsgrund.GOZ_UNCLEAR);
        assertThat(bewertung.get("erstattungsvorschlag")).isNull();
        assertThat(bewertung.get("arztauskunft")).isNull();
    }

    @Test
    @DisplayName("an incomplete Arztauskunft is not carried at all")
    void anIncompleteArztauskunftIsLeftOut() {
        Schreibtool tool = new Schreibtool();
        BewertungResult withoutHint = new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                List.of(new Eskalationsgrund(Eskalationsgrund.ARZT_FLAGGED, "…")), List.of(),
                IMAGES.readTree("{\"plausibilitaet\":\"auffaellig\",\"notwendigkeit\":\"ueblich\"}"),
                "Bitte ansehen.");

        writer.write(50071L, 4711L, withoutHint, POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(IMAGES.readTree(tool.arguments).get("bewertung").get("arztauskunft")).isNull();
    }

    @Test
    @DisplayName("an uppercase Arztauskunft becomes the contract value")
    void arztauskunftIsNormalized() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withArztauskunft("""
                {"plausibilitaet":"PLAUSIBEL","notwendigkeit":" Ueblich ",
                 "text":"Passt zusammen.","hinweis":"Sprachmodell, kein Beleg."}
                """), POSITIONEN_IM_FALL, List.of(), "modell", tool);

        JsonNode info = IMAGES.readTree(tool.arguments).get("bewertung").get("arztauskunft");
        assertThat(info.get("plausibilitaet").asString()).isEqualTo("plausibel");
        assertThat(info.get("notwendigkeit").asString()).isEqualTo("ueblich");
    }

    @Test
    @DisplayName("an Arztauskunft with an invented value is not carried at all")
    void anUnknownArztauskunftIsLeftOut() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withArztauskunft("""
                {"plausibilitaet":"teilweise","notwendigkeit":"ueblich",
                 "text":"Passt halb.","hinweis":"Sprachmodell, kein Beleg."}
                """), POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(IMAGES.readTree(tool.arguments).get("bewertung").get("arztauskunft")).isNull();
    }

    @Test
    @DisplayName("a lowercase Zustand becomes the contract value and not a failure")
    void theZustandIsNormalized() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position("2197", "zerh", "enthalten", "Gedeckt.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        JsonNode position = positionen(tool).get(0);
        assertThat(position.get("zustand").asString()).isEqualTo("ENTHALTEN");
        assertThat(position.get("leistungsbereich").asString()).isEqualTo("ZERH");
    }

    @Test
    @DisplayName("an invented Zustand costs the Position, not the call")
    void anUnknownZustandDropsOut() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position("2197", "ZERH", "unklar", "Weiss nicht."),
                new Bewertungsvorschlag.Position("2150", "ZERH", "ENTHALTEN", "Gedeckt.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(positionen(tool).size()).isEqualTo(1);
        assertThat(positionen(tool).get(0).get("goz").asString()).isEqualTo("2150");
    }

    @Test
    @DisplayName("an invented Leistungsbereich costs only the field")
    void anUnknownLeistungsbereichCostsOnlyTheField() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position("2197", "Zahnersatz", "ENTHALTEN", "Gedeckt.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        JsonNode position = positionen(tool).get(0);
        assertThat(position.get("goz").asString()).isEqualTo("2197");
        assertThat(position.get("leistungsbereich")).isNull();
    }

    @Test
    @DisplayName("a lowercase Leistungsbereich becomes the contract value")
    void theLeistungsbereichIsNormalized() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position("2197", " ze ", "ENTHALTEN", "Gedeckt.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(positionen(tool).get(0).get("leistungsbereich").asString()).isEqualTo("ZE");
    }

    @Test
    @DisplayName("if every Position drops out, the Bewertung still goes out")
    void withoutAUsablePositionItStillWrites() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position("2197", "ZERH", "unklar", "Weiss nicht.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(positionen(tool)).isEmpty();
        assertThat(IMAGES.readTree(tool.arguments).get("bewertung").get("eskalationsgruende")
                .size()).isEqualTo(1);
    }

    @Test
    @DisplayName("a Position without a usable GOZ Nummer drops out")
    void anUnusablePositionDropsOut() {
        Schreibtool tool = new Schreibtool();
        BewertungResult withGarbage = new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                List.of(new Eskalationsgrund(Eskalationsgrund.GOZ_UNCLEAR, "…")),
                List.of(new Bewertungsvorschlag.Position("2197", "ZERH", "ENTHALTEN", "Gedeckt."),
                        new Bewertungsvorschlag.Position("Materialkosten", null, "UNBEKANNT", "x"),
                        new Bewertungsvorschlag.Position("2150", null, null, null)),
                null, "Bitte ansehen.");

        writer.write(50071L, 4711L, withGarbage, POSITIONEN_IM_FALL, List.of(), "modell", tool);

        JsonNode positionen = IMAGES.readTree(tool.arguments).get("bewertung").get("positionen");
        assertThat(positionen.size()).isEqualTo(1);
        assertThat(positionen.get(0).get("goz").asString()).isEqualTo("2197");
    }

    @Test
    @DisplayName("a Position without a Gebuehrennummer is written with its index")
    void aPositionWithoutANummerIsCarried() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone."),
                new Bewertungsvorschlag.Position(1, null, "ZE", "NICHT_BESTIMMBAR",
                        "Laborkosten zur Krone an Zahn 46.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(positionen(tool).size()).isEqualTo(2);
        JsonNode withoutZiffer = positionen(tool).get(1);
        assertThat(withoutZiffer.get("index").asInt()).isEqualTo(1);
        assertThat(withoutZiffer.get("goz")).isNull();
        assertThat(withoutZiffer.get("leistungsbereich").asString()).isEqualTo("ZE");
    }

    @Test
    @DisplayName("without a Nummer and without an index the Position drops out")
    void aPositionWithoutAKeyDropsOut() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position(null, null, "ZE", "ENTHALTEN", "Irgendwas."),
                new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone.")),
                POSITIONEN_IM_FALL, List.of(), "modell", tool);

        assertThat(positionen(tool).size()).isEqualTo(1);
        assertThat(positionen(tool).get(0).get("goz").asString()).isEqualTo("2197");
    }

    @Test
    @DisplayName("an index outside the Schadensfall drops out instead of failing the call")
    void anIndexOutsideTheSchadensfallDropsOut() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position(0, "2197", "ZE", "ENTHALTEN", "Krone."),
                new Bewertungsvorschlag.Position(9, null, "ZE", "NICHT_BESTIMMBAR", "Labor.")),
                2, List.of(), "modell", tool);

        assertThat(positionen(tool).size()).isEqualTo(1);
        assertThat(positionen(tool).get(0).get("index").asInt()).isZero();
    }

    @Test
    @DisplayName("a negative index drops out too")
    void aNegativeIndexDropsOut() {
        Schreibtool tool = new Schreibtool();

        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position(-1, null, "ZE", "ENTHALTEN", "Labor.")),
                2, List.of(), "modell", tool);

        assertThat(positionen(tool)).isEmpty();
    }

    @Test
    @DisplayName("the limit is exclusive: index 1 stays with two Positionen, index 2 drops")
    void theLimitIsExclusive() {
        Schreibtool inside = new Schreibtool();
        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position(1, null, "ZE", "ENTHALTEN", "Labor.")),
                2, List.of(), "modell", inside);

        Schreibtool outside = new Schreibtool();
        writer.write(50071L, 4711L, withPositionen(
                new Bewertungsvorschlag.Position(2, null, "ZE", "ENTHALTEN", "Labor.")),
                2, List.of(), "modell", outside);

        assertThat(positionen(inside).size()).isEqualTo(1);
        assertThat(positionen(inside).get(0).get("index").asInt()).isEqualTo(1);
        assertThat(positionen(outside)).isEmpty();
    }

    @Test
    @DisplayName("a conflict from the Kernsystem gets its own exception")
    void aConflictIsDetected() {
        Schreibtool tool = new Schreibtool();
        tool.failure = new ToolExecutionException(tool.getToolDefinition(),
                new IllegalStateException("Error calling tool: [TextContent[text=Error invoking "
                        + "method: Eine Bewertung ist nur aus in_pruefung moeglich, der Fall "
                        + "steht auf genehmigt]]"));

        assertThatThrownBy(() ->
                writer.write(50071L, 4711L, freigabe(), POSITIONEN_IM_FALL, List.of(), "modell", tool))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("50071");
    }

    @Test
    @DisplayName("the word Konflikt alone is enough too")
    void conflictOnTheWord() {
        Schreibtool tool = new Schreibtool();
        tool.failure = new IllegalStateException("Konflikt: Fall steht auf genehmigt");

        assertThatThrownBy(() ->
                writer.write(50071L, 4711L, freigabe(), POSITIONEN_IM_FALL, List.of(), "modell", tool))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("any other failure stays a failure")
    void anotherFailureIsLeftAlone() {
        Schreibtool tool = new Schreibtool();
        tool.failure = new IllegalStateException("Error calling tool: connection refused");

        assertThatThrownBy(() ->
                writer.write(50071L, 4711L, freigabe(), POSITIONEN_IM_FALL, List.of(), "modell", tool))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ConflictException.class);
    }


    private static JsonNode positionen(Schreibtool tool) {
        return IMAGES.readTree(tool.arguments).get("bewertung").get("positionen");
    }

    private static BewertungResult withArztauskunft(String auskunft) {
        return new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                List.of(new Eskalationsgrund(Eskalationsgrund.ARZT_FLAGGED, "…")), List.of(),
                IMAGES.readTree(auskunft), "Bitte ansehen.");
    }

    private static BewertungResult withPositionen(Bewertungsvorschlag.Position... positionen) {
        return new BewertungResult(Bewertungsvorschlag.ESKALATION, null,
                List.of(new Eskalationsgrund(Eskalationsgrund.GOZ_UNCLEAR, "Zu 9010 fehlt ein Befund.")),
                List.of(positionen), null, "Bitte ansehen.");
    }

    private static BewertungResult freigabe() {
        return new BewertungResult(Bewertungsvorschlag.FREIGABE, new BigDecimal("336.000"),
                List.of(),
                List.of(new Bewertungsvorschlag.Position("2197", "ZERH", "ENTHALTEN",
                        "Zahnerhalt, 85 Prozent laut B.2.1")),
                IMAGES.readTree("""
                        {"plausibilitaet":"plausibel","notwendigkeit":"ueblich",
                         "text":"Passt zusammen.","hinweis":"Sprachmodell, kein Beleg.",
                         "modell":"gemini-3.6-flash"}
                        """),
                "Beide Positionen sind gedeckt.");
    }

    private static final class Schreibtool implements ToolCallback {

        private String arguments;
        private Long kundenId;
        private RuntimeException failure;

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(ToolSelection.BEWERTEN).description("Test")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
        }

        @Override
        public String call(String input) {
            return call(input, null);
        }

        @Override
        public String call(String input, ToolContext context) {
            this.arguments = input;
            this.kundenId = MandantContext.caller();
            if (failure != null) {
                throw failure;
            }
            return "{\"status\":\"geprueft_freigabe\"}";
        }
    }
}
