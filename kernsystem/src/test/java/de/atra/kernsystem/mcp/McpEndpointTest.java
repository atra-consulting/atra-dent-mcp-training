package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpEndpointTest extends WithMcpClient {

    private static final List<String> TOOLS = List.of(
            "mein_vertrag_lesen",
            "meine_kontaktdaten_aendern",
            "meinen_tarif_wechseln",
            "meine_schadensfaelle_auflisten",
            "schadensfall_lesen",
            "schadensfall_einreichen",
            "schadensfall_bewerten",
            "mein_beitrag_berechnen",
            "rechnung_extrahieren");

    @Test
    @DisplayName("the server announces its name and offers exactly the nine tools")
    void listTools() {
        var client = clientFor(ANNA);

        assertThat(client.getServerInfo().name()).isEqualTo("atra-dent-kernsystem");
        assertThat(client.listTools().tools())
                .extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrderElementsOf(TOOLS);
    }

    @Test
    @DisplayName("every tool carries a description -- it is the only thing a model sees beforehand")
    void everyToolIsDescribed() {
        assertThat(clientFor(ANNA).listTools().tools())
                .allSatisfy(tool -> assertThat(tool.description())
                        .as("Beschreibung von %s", tool.name())
                        .isNotBlank()
                        .hasSizeGreaterThan(200));
    }

    @Test
    @DisplayName("every tool says whether it reads or writes, and none reaches into an open world")
    void everyToolCarriesItsAnnotations() {
        Set<String> reading = Set.of("mein_vertrag_lesen", "meine_schadensfaelle_auflisten",
                "schadensfall_lesen", "mein_beitrag_berechnen", "rechnung_extrahieren");

        assertThat(clientFor(ANNA).listTools().tools()).allSatisfy(tool -> {
            McpSchema.ToolAnnotations annotations = tool.annotations();
            assertThat(annotations).as("Annotationen von %s", tool.name()).isNotNull();
            assertThat(annotations.readOnlyHint())
                    .as("readOnlyHint von %s", tool.name())
                    .isEqualTo(reading.contains(tool.name()));
            assertThat(annotations.openWorldHint())
                    .as("openWorldHint von %s", tool.name())
                    .isFalse();
        });
    }

    @Test
    @DisplayName("einreichen only adds; bewerten, aendern and wechseln overwrite what is there")
    void writingToolsSeparateAddingFromOverwriting() {
        assertThat(tool("schadensfall_einreichen").annotations().destructiveHint()).isFalse();
        assertThat(tool("schadensfall_bewerten").annotations().destructiveHint()).isTrue();
        assertThat(tool("meine_kontaktdaten_aendern").annotations().destructiveHint()).isTrue();
        assertThat(tool("meinen_tarif_wechseln").annotations().destructiveHint()).isTrue();
    }

    @Test
    @DisplayName("no tool accepts a Kundennummer")
    void noToolHasAKundenParameter() {
        for (McpSchema.Tool tool : clientFor(ANNA).listTools().tools()) {
            Object fields = tool.inputSchema().get("properties");
            Map<?, ?> parameter = fields instanceof Map<?, ?> abbildung ? abbildung : Map.of();
            assertThat(parameter.keySet().stream().map(String::valueOf).toList())
                    .as("Parameter von %s", tool.name())
                    .doesNotContain("kundenId", "kunden_id", "kundennummer");
        }
    }

    @Test
    @DisplayName("the server instructions name where the Kundennummer comes from")
    void instructionsNameTheHeader() {
        assertThat(clientFor(ANNA).getServerInstructions()).contains("x-kunden-id");
    }

    @Test
    @DisplayName("the Betrag of a Rechnungsposition is expected as a string, not as a number")
    void betragIsAStringInTheSchema() {
        McpSchema.Tool submit = clientFor(ANNA).listTools().tools().stream()
                .filter(tool -> tool.name().equals("schadensfall_einreichen"))
                .findFirst()
                .orElseThrow();

        assertThat(typeInSchema(submit.inputSchema(),
                "properties", "positionen", "items", "properties", "betrag", "type"))
                .isEqualTo("string");
    }

    @Test
    @DisplayName("the schema of schadensfall_bewerten names the index as the key of the Befund")
    void indexIsInTheSchemaOfBewerten() {
        assertThat(description("schadensfall_bewerten", "bewertung", "positionen"))
                .contains("index")
                .contains("schadensfall.positionen")
                .contains("Immer angeben");
    }

    @Test
    @DisplayName("the schema of schadensfall_einreichen carries goz, zahn, datum and anzahl per Position")
    void positionFieldsAreInTheSchemaOfEinreichen() {
        Object fields = typeInSchema(tool("schadensfall_einreichen").inputSchema(),
                "properties", "positionen", "items", "properties");
        assertThat(fields).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) fields).keySet().stream().map(String::valueOf).toList())
                .contains("goz", "zahn", "datum", "anzahl", "betrag", "beschreibung",
                        "leistungsbereich");
    }

    @Test
    @DisplayName("the tool text for the Positionen names every field the schema offers")
    void toolTextNamesEveryFieldOfTheSchema() {
        Object fields = typeInSchema(tool("schadensfall_einreichen").inputSchema(),
                "properties", "positionen", "items", "properties");
        String toolText = description("schadensfall_einreichen", "positionen");

        assertThat(((Map<?, ?>) fields).keySet().stream().map(String::valueOf).toList())
                .allSatisfy(field -> assertThat(toolText)
                        .as("Der Tooltext zu positionen erklaert %s nicht", field)
                        .contains(field));
    }

    @Test
    @DisplayName("the tool text says a Position without a Gebuehrennummer still belongs in the Schadensfall")
    void toolTextKeepsPositionenWithoutANummerInTheSchadensfall() {
        assertThat(description("schadensfall_einreichen", "positionen"))
                .contains("ALLE")
                .contains("Material")
                .contains("gehört mit in den Fall");
    }

    private String description(String werkzeug, String... feldpfad) {
        List<String> path = new ArrayList<>();
        for (String field : feldpfad) {
            path.add("properties");
            path.add(field);
        }
        path.add("description");
        Object text = typeInSchema(tool(werkzeug).inputSchema(), path.toArray(String[]::new));
        assertThat(text).as("Beschreibung von %s.%s", werkzeug, String.join(".", feldpfad))
                .isInstanceOf(String.class);
        return (String) text;
    }

    private McpSchema.Tool tool(String name) {
        return clientFor(ANNA).listTools().tools().stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein Werkzeug mit dem Namen " + name));
    }

    @SuppressWarnings("unchecked")
    private static Object typeInSchema(Map<String, Object> schema, String... path) {
        Object spot = schema;
        for (String teil : path) {
            assertThat(spot).as("Zwischenstufe vor '%s'", teil).isInstanceOf(Map.class);
            spot = ((Map<String, Object>) spot).get(teil);
        }
        return spot;
    }

    private static final Set<String> FORBIDDEN_JAVA_CONSTANTS = Set.of(
            "EINGEREICHT", "IN_PRUEFUNG", "GEPRUEFT_FREIGABE", "GEPRUEFT_ESKALATION",
            "GENEHMIGT", "ABGELEHNT", "AUSGEZAHLT",
            "FREIGABE", "ESKALATION",
            "KUNDE", "AGENT", "SACHBEARBEITUNG", "SYSTEM",
            "MODELL", "TOOL", "A2A",
            "PLAUSIBEL", "AUFFAELLIG", "UEBLICH", "FRAGLICH");

    @Test
    @DisplayName("no input schema contains an enum value as a Java constant name instead of the contract value")
    void noEnumAsAJavaConstant() {
        for (McpSchema.Tool tool : clientFor(ANNA).listTools().tools()) {
            List<String> enumValues = new ArrayList<>();
            collectEnumValues(tool.inputSchema(), enumValues);
            assertThat(enumValues)
                    .as("Enum-Werte im Eingabeschema von %s", tool.name())
                    .doesNotContainAnyElementsOf(FORBIDDEN_JAVA_CONSTANTS);
        }
    }

    private static void collectEnumValues(Object node, List<String> target) {
        if (node instanceof Map<?, ?> abbildung) {
            for (Map.Entry<?, ?> entry : abbildung.entrySet()) {
                if ("enum".equals(entry.getKey()) && entry.getValue() instanceof List<?> values) {
                    values.stream().map(String::valueOf).forEach(target::add);
                } else {
                    collectEnumValues(entry.getValue(), target);
                }
            }
        } else if (node instanceof List<?> liste) {
            liste.forEach(element -> collectEnumValues(element, target));
        }
    }
}
