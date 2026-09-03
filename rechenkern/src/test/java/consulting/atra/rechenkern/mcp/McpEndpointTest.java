package consulting.atra.rechenkern.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpEndpointTest {

    private static final List<String> TOOLS = List.of(
            "tarife_auflisten",
            "tarif_lesen",
            "beitrag_berechnen",
            "erstattung_berechnen");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    private McpSyncClient client() {
        if (client == null) {
            client = McpClient.sync(HttpClientStreamableHttpTransport
                            .builder("http://localhost:" + port)
                            .endpoint("/mcp")
                            .build())
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();
            client.initialize();
        }
        return client;
    }

    @AfterEach
    void closeConnection() {
        if (client != null) {
            client.closeGracefully();
            client = null;
        }
    }

    @Test
    @DisplayName("the server announces its name and offers exactly the four tools")
    void listTools() {
        assertThat(client().getServerInfo().name()).isEqualTo("atra-dent-rechenkern");
        assertThat(client().listTools().tools())
                .extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrderElementsOf(TOOLS);
    }

    @Test
    @DisplayName("every tool carries a description -- it is the only thing a model sees beforehand")
    void everyToolIsDescribed() {
        assertThat(client().listTools().tools())
                .allSatisfy(tool -> assertThat(tool.description())
                        .as("description of %s", tool.name())
                        .isNotBlank()
                        .hasSizeGreaterThan(200));
    }

    @Test
    @DisplayName("every tool declares itself read-only -- the Rechenkern only calculates")
    void everyToolIsAnnotatedReadOnly() {
        assertThat(client().listTools().tools()).allSatisfy(tool -> {
            McpSchema.ToolAnnotations annotations = tool.annotations();
            assertThat(annotations).as("annotations of %s", tool.name()).isNotNull();
            assertThat(annotations.readOnlyHint()).as("readOnlyHint of %s", tool.name()).isTrue();
            assertThat(annotations.destructiveHint())
                    .as("destructiveHint of %s", tool.name()).isFalse();
            assertThat(annotations.openWorldHint())
                    .as("openWorldHint of %s", tool.name()).isFalse();
        });
    }

    @Test
    @DisplayName("the server instructions say that the Rechenkern knows no Kunde")
    void instructionsNameTheMandantengrenze() {
        assertThat(client().getServerInstructions()).contains("keinen Kunden");
    }

    @Test
    @DisplayName("the input schema of erstattung_berechnen does not make goz required")
    void positionenNeedNoGebuehrennummer() {
        McpSchema.Tool erstattung = client().listTools().tools().stream()
                .filter(tool -> "erstattung_berechnen".equals(tool.name()))
                .findFirst()
                .orElseThrow();

        JsonNode position = MAPPER.valueToTree(erstattung.inputSchema())
                .path("properties").path("positionen").path("items");

        assertThat(position.path("properties").has("goz"))
                .as("goz is still in the schema, only not required")
                .isTrue();
        assertThat(position.path("required").valueStream().map(JsonNode::asString).toList())
                .as("required fields of a Position")
                .containsExactlyInAnyOrder("leistungsbereich", "betrag", "beschreibung");
    }

    @Test
    @DisplayName("the input schema of verbrauch makes not a single field required")
    void verbrauchIsOptionalThroughout() {
        JsonNode verbrauch = inputSchema("erstattung_berechnen")
                .path("properties").path("verbrauch");

        assertThat(verbrauch.path("properties").propertyNames())
                .as("the five fields are still in the schema")
                .containsExactlyInAnyOrder("staffel", "selbstbehalt", "jahr",
                        "leistungsbereiche", "leistungsbereicheSeitVersicherungsbeginn");
        assertThat(verbrauch.path("required").valueStream().map(JsonNode::asString).toList())
                .as("required fields of the Vorverbrauch -- the contract has none")
                .isEmpty();
    }

    @Test
    @DisplayName("erstattung_berechnen requires exactly the four fields the contract requires")
    void erstattungRequiresOnlyTheFourRequiredFields() {
        assertThat(inputSchema("erstattung_berechnen")
                .path("required").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("tarifId", "versicherungsbeginn",
                        "behandlungsdatum", "positionen");
    }

    private JsonNode inputSchema(String tool) {
        return MAPPER.valueToTree(client().listTools().tools().stream()
                .filter(candidate -> tool.equals(candidate.name()))
                .findFirst()
                .orElseThrow()
                .inputSchema());
    }
}
