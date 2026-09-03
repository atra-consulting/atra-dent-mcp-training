package consulting.atra.agenten.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolSchemaInlinerTest {

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final class Testtool implements ToolCallback {

        private final String name;
        private final String schema;
        private String gerufenMit;
        private ToolContext gerufenerContext;

        Testtool(String name, String schema) {
            this.name = name;
            this.schema = schema;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return DefaultToolDefinition.builder()
                    .name(name)
                    .description("Ein Tool zum Prüfen")
                    .inputSchema(schema)
                    .build();
        }

        @Override
        public String call(String toolInput) {
            this.gerufenMit = toolInput;
            return "geantwortet";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            this.gerufenMit = toolInput;
            this.gerufenerContext = toolContext;
            return "geantwortet mit Context";
        }
    }

    private static JsonNode schemaOf(ToolCallback callback) {
        return IMAGES.readTree(callback.getToolDefinition().inputSchema());
    }

    @Test
    @DisplayName("without $defs the same callback comes back, unchanged")
    void withoutDefsEverythingStaysAsItWas() {
        var tool = new Testtool("goz_pruefen", """
                {"type":"object","properties":{"goz":{"type":"string"}}}""");

        assertThat(ToolSchemaInliner.resolve(tool)).isSameAs(tool);
        assertThat(ToolSchemaInliner.resolve(List.of(tool))).containsExactly(tool);
    }

    @Test
    @DisplayName("a nested object is inlined in its place")
    void aNestedObjectIsInlined() {
        var tool = new Testtool("fall_pruefen", """
                {
                  "type": "object",
                  "$defs": {
                    "Anschrift": {
                      "type": "object",
                      "properties": {"ort": {"type": "string"}}
                    }
                  },
                  "properties": {
                    "anschrift": {"$ref": "#/$defs/Anschrift"}
                  }
                }""");

        JsonNode schema = schemaOf(ToolSchemaInliner.resolve(tool));

        assertThat(schema.get("$defs")).isNull();
        JsonNode address = schema.get("properties").get("anschrift");
        assertThat(address.get("$ref")).isNull();
        assertThat(address.get("type").asString()).isEqualTo("object");
        assertThat(address.get("properties").get("ort").get("type").asString())
                .isEqualTo("string");
    }

    @Test
    @DisplayName("the $ref under items of a list is resolved too")
    void aListOfRecordsIsResolved() {
        var tool = new Testtool("bewertung_abgeben", """
                {
                  "type": "object",
                  "$defs": {
                    "Positionsbefund": {
                      "type": "object",
                      "properties": {"goz": {"type": "string"}},
                      "required": ["goz"]
                    }
                  },
                  "properties": {
                    "positionen": {
                      "type": "array",
                      "items": {"$ref": "#/$defs/Positionsbefund"}
                    }
                  }
                }""");

        JsonNode schema = schemaOf(ToolSchemaInliner.resolve(tool));

        assertThat(schema.get("$defs")).isNull();
        JsonNode items = schema.get("properties").get("positionen").get("items");
        assertThat(items.get("$ref")).isNull();
        assertThat(items.get("properties").get("goz").get("type").asString()).isEqualTo("string");
        assertThat(items.get("required")).hasSize(1);
    }

    @Test
    @DisplayName("the same definition referenced twice appears twice afterwards")
    void theSameDefinitionTwice() {
        var tool = new Testtool("erstattung_berechnen", """
                {
                  "type": "object",
                  "$defs": {
                    "Map_String.String_": {"type": "object"}
                  },
                  "properties": {
                    "verbrauch": {
                      "type": "object",
                      "properties": {
                        "leistungsbereiche": {"$ref": "#/$defs/Map_String.String_"},
                        "leistungsbereicheSeitVersicherungsbeginn": {
                          "$ref": "#/$defs/Map_String.String_"
                        }
                      }
                    }
                  }
                }""");

        JsonNode schema = schemaOf(ToolSchemaInliner.resolve(tool));

        assertThat(schema.get("$defs")).isNull();
        JsonNode usage = schema.get("properties").get("verbrauch").get("properties");
        assertThat(usage.get("leistungsbereiche").get("type").asString()).isEqualTo("object");
        assertThat(usage.get("leistungsbereicheSeitVersicherungsbeginn").get("type").asString())
                .isEqualTo("object");
    }

    @Test
    @DisplayName("the same definition twice, its own description twice")
    void twoUsagesKeepTheirOwnDescription() {
        var tool = new Testtool("erstattung_berechnen", """
                {
                  "type": "object",
                  "$defs": {
                    "Map_String.String_": {
                      "type": "object",
                      "additionalProperties": {"type": "string"},
                      "description": "generisch"
                    }
                  },
                  "properties": {
                    "leistungsbereiche": {
                      "$ref": "#/$defs/Map_String.String_",
                      "description": "Im laufenden Versicherungsjahr ausgezahlt."
                    },
                    "leistungsbereicheSeitVersicherungsbeginn": {
                      "$ref": "#/$defs/Map_String.String_",
                      "description": "Seit Versicherungsbeginn ausgezahlt."
                    }
                  }
                }""");

        JsonNode properties = schemaOf(ToolSchemaInliner.resolve(tool)).get("properties");

        JsonNode running = properties.get("leistungsbereiche");
        JsonNode sinceStart = properties.get("leistungsbereicheSeitVersicherungsbeginn");
        assertThat(running.get("description").asString())
                .isEqualTo("Im laufenden Versicherungsjahr ausgezahlt.");
        assertThat(sinceStart.get("description").asString())
                .isEqualTo("Seit Versicherungsbeginn ausgezahlt.");
        assertThat(running.get("additionalProperties").get("type").asString())
                .isEqualTo("string");
        assertThat(sinceStart.get("additionalProperties").get("type").asString())
                .isEqualTo("string");
    }

    @Test
    @DisplayName("a $ref at the root is resolved too")
    void aRefAtTheRootIsResolved() {
        var tool = new Testtool("fall_pruefen", """
                {
                  "$ref": "#/$defs/Eingabe",
                  "$defs": {
                    "Eingabe": {
                      "type": "object",
                      "properties": {"goz": {"type": "string"}}
                    }
                  }
                }""");

        JsonNode schema = schemaOf(ToolSchemaInliner.resolve(tool));

        assertThat(schema.toString()).doesNotContain("$ref").doesNotContain("$defs");
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("properties").get("goz").get("type").asString())
                .isEqualTo("string");
    }

    @Test
    @DisplayName("a definition that is not an object does not silently swallow its siblings")
    void aNonObjectWithSiblingsIsReported() {
        var tool = new Testtool("fall_pruefen", """
                {
                  "type": "object",
                  "$defs": {"Alles": true},
                  "properties": {
                    "feld": {"$ref": "#/$defs/Alles", "description": "wichtig"}
                  }
                }""");

        assertThatThrownBy(() -> ToolSchemaInliner.resolve(tool))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fall_pruefen")
                .hasMessageContaining("#/$defs/Alles")
                .hasMessageContaining("description");
    }

    @Test
    @DisplayName("an unreadable schema is reported with the name of the tool")
    void anUnreadableSchemaNamesTheTool() {
        var tool = new Testtool("erstattung_berechnen",
                "{\"$defs\": {\"Wert\": {\"type\": \"string\"}}");

        assertThatThrownBy(() -> ToolSchemaInliner.resolve(tool))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("erstattung_berechnen");
    }

    @Test
    @DisplayName("what stands next to a $ref stays")
    void siblingsNextToARefAreKept() {
        var tool = new Testtool("erstattung_berechnen", """
                {
                  "type": "object",
                  "$defs": {
                    "Map_String.String_": {"type": "object", "description": "generisch"}
                  },
                  "properties": {
                    "leistungsbereiche": {
                      "$ref": "#/$defs/Map_String.String_",
                      "description": "Im laufenden Versicherungsjahr ausgezahlt."
                    }
                  }
                }""");

        JsonNode resolved = schemaOf(ToolSchemaInliner.resolve(tool))
                .get("properties").get("leistungsbereiche");

        assertThat(resolved.get("$ref")).isNull();
        assertThat(resolved.get("type").asString()).isEqualTo("object");
        assertThat(resolved.get("description").asString())
                .isEqualTo("Im laufenden Versicherungsjahr ausgezahlt.");
    }

    @Test
    @DisplayName("a $ref in anyOf and a $ref in a definition are resolved too")
    void anyOfAndChainingAreResolved() {
        var tool = new Testtool("fall_pruefen", """
                {
                  "type": "object",
                  "$defs": {
                    "Aeusseres": {
                      "type": "object",
                      "properties": {"inneres": {"$ref": "#/$defs/Inneres"}}
                    },
                    "Inneres": {"type": "string", "description": "ganz innen"}
                  },
                  "properties": {
                    "feld": {
                      "anyOf": [{"$ref": "#/$defs/Aeusseres"}, {"type": "null"}]
                    }
                  }
                }""");

        JsonNode schema = schemaOf(ToolSchemaInliner.resolve(tool));

        assertThat(schema.get("$defs")).isNull();
        JsonNode firstVariant = schema.get("properties").get("feld").get("anyOf").get(0);
        assertThat(firstVariant.get("properties").get("inneres").get("description").asString())
                .isEqualTo("ganz innen");
        assertThat(schema.toString()).doesNotContain("$ref");
    }

    @Test
    @DisplayName("a cycle is reported loudly, not cut off")
    void aCycleIsReported() {
        var tool = new Testtool("fall_pruefen", """
                {
                  "type": "object",
                  "$defs": {
                    "Knoten": {
                      "type": "object",
                      "properties": {"kind": {"$ref": "#/$defs/Knoten"}}
                    }
                  },
                  "properties": {"wurzel": {"$ref": "#/$defs/Knoten"}}
                }""");

        assertThatThrownBy(() -> ToolSchemaInliner.resolve(tool))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fall_pruefen")
                .hasMessageContaining("Knoten -> Knoten");
    }

    @Test
    @DisplayName("a $ref without a definition is reported loudly, not dropped")
    void anUnknownRefIsReported() {
        var tool = new Testtool("fall_pruefen", """
                {
                  "type": "object",
                  "$defs": {"Da": {"type": "string"}},
                  "properties": {"feld": {"$ref": "#/$defs/Fehlt"}}
                }""");

        assertThatThrownBy(() -> ToolSchemaInliner.resolve(tool))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fall_pruefen")
                .hasMessageContaining("#/$defs/Fehlt");
    }

    @Test
    @DisplayName("name, description and both call shapes pass through unchanged")
    void onlyTheSchemaChanges() {
        var tool = new Testtool("erstattung_berechnen", """
                {
                  "type": "object",
                  "$defs": {"Wert": {"type": "string"}},
                  "properties": {"feld": {"$ref": "#/$defs/Wert"}}
                }""");

        ToolCallback resolved = ToolSchemaInliner.resolve(tool);

        assertThat(resolved.getToolDefinition().name()).isEqualTo("erstattung_berechnen");
        assertThat(resolved.getToolDefinition().description())
                .isEqualTo(tool.getToolDefinition().description());
        assertThat(resolved.getToolMetadata().returnDirect())
                .isEqualTo(tool.getToolMetadata().returnDirect());

        assertThat(resolved.call("{\"feld\":\"a\"}")).isEqualTo("geantwortet");
        assertThat(tool.gerufenMit).isEqualTo("{\"feld\":\"a\"}");

        ToolContext context = new ToolContext(Map.of("x-kunden-id", "K-1"));
        assertThat(resolved.call("{\"feld\":\"b\"}", context))
                .isEqualTo("geantwortet mit Context");
        assertThat(tool.gerufenMit).isEqualTo("{\"feld\":\"b\"}");
        assertThat(tool.gerufenerContext).isSameAs(context);
    }
}
