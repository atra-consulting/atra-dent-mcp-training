package consulting.atra.wissen.mcp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import consulting.atra.wissen.api.TestEmbedding;
import consulting.atra.wissen.goz.GozBefund;
import consulting.atra.wissen.goz.GozPruefung;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.model.embedding=none")
@Import(TestEmbedding.class)
class WissenToolsTest {

    private static final JsonMapper JSON = new JsonMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private GozPruefung pruefung;

    private McpSyncClient client;

    @BeforeEach
    void openSession() {
        client = McpClient.sync(HttpClientStreamableHttpTransport
                        .builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
    }

    @AfterEach
    void closeSession() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    @DisplayName("both tools are registered under their names")
    void toolsAreRegistered() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names).contains("bedingungen_suchen", "goz_pruefen").hasSize(5);
    }

    @Test
    @DisplayName("the Tarif is required in both input schemas and lists the four keys")
    void tarifIsRequired() {
        for (String name : List.of("bedingungen_suchen", "goz_pruefen")) {
            DocumentContext read = schema(name);
            List<String> mandatory = read.read("$.required");
            assertThat(mandatory).as("Pflichtfelder von %s", name).contains("tarif");

            List<String> tarife = read.read("$.properties.tarif.enum");
            assertThat(tarife).containsExactlyInAnyOrder(
                    "ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");

            String description = read.read("$.properties.tarif.description");
            assertThat(description).contains("atra.dent.brillant mit Selbstbehalt");
        }
    }

    @Test
    @DisplayName("the description of goz_pruefen names the four Zustaende and forbids the rejection")
    void theDescriptionWalksThroughTheStates() {
        String description = tool("goz_pruefen").description();
        assertThat(description)
                .contains("ENTHALTEN", "NICHT_ENTHALTEN", "NICHT_BESTIMMBAR", "UNBEKANNT")
                .contains("KEINE Ablehnung")
                .contains("Rückfrage")
                .contains("berechnet keine Erstattung");
    }

    @Test
    @DisplayName("goz_pruefen returns the same Befunde as calling the domain logic directly")
    void checkGozCarriesTheDomainLogic() {
        List<String> numbers = List.of("9010", "6030", "1040", "0010", "9999", "9010");

        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("goz_pruefen")
                .arguments(Map.of("tarif", "ATRA_DENT_B", "nummern", numbers))
                .build());
        assertThat(response.isError()).isNotEqualTo(Boolean.TRUE);
        DocumentContext read = JsonPath.parse(text(response));
        assertThat(read.<String>read("$.tarif")).isEqualTo("ATRA_DENT_B");

        List<GozBefund> expected = pruefung.check("ATRA_DENT_B", numbers);
        assertThat(read.<List<String>>read("$.befunde[*].nummer"))
                .containsExactlyElementsOf(expected.stream().map(GozBefund::nummer).toList());
        assertThat(read.<List<String>>read("$.befunde[*].status"))
                .containsExactlyElementsOf(expected.stream().map(befund -> befund.status().name()).toList());
        assertThat(read.<List<String>>read("$.befunde[*].begruendung"))
                .containsExactlyElementsOf(expected.stream().map(GozBefund::begruendung).toList());

        assertThat(read.<Integer>read("$.befunde[0].quote")).isEqualTo(85);
        assertThat(read.<String>read("$.befunde[2].grenzen.limitProJahr")).isEqualTo("150.00");
        assertThat(text(response)).doesNotContain("null");
    }

    @Test
    @DisplayName("bedingungen_suchen searches only the documents of the Tarif")
    void searchBedingungenKeepsTheTarifFilter() {
        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("bedingungen_suchen")
                .arguments(Map.of("tarif", "ATRA_DENT_S", "frage", "Wie lange ist die Wartezeit?", "anzahl", 3))
                .build());
        assertThat(response.isError()).isNotEqualTo(Boolean.TRUE);

        DocumentContext read = JsonPath.parse(text(response));
        assertThat(read.<String>read("$.tarif")).isEqualTo("ATRA_DENT_S");
        assertThat(read.<String>read("$.frage")).isEqualTo("Wie lange ist die Wartezeit?");

        List<String> documents = read.read("$.treffer[*].dokumentId");
        assertThat(documents).hasSize(3).allSatisfy(dokument -> assertThat(dokument).isIn(
                "atra-dent-smart-avb", "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung"));

        List<String> htmlUrls = read.read("$.treffer[*].htmlUrl");
        assertThat(htmlUrls).allSatisfy(url -> assertThat(url).contains("#"));
    }

    @Test
    @DisplayName("without anzahl the five hits of the contract apply")
    void searchBedingungenWithoutAnzahl() {
        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("bedingungen_suchen")
                .arguments(Map.of("tarif", "ATRA_DENT_X", "frage", "Was ist ein Implantat?"))
                .build());
        assertThat(response.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(JsonPath.parse(text(response)).<List<String>>read("$.treffer[*].dokumentId")).hasSize(5);
    }

    private DocumentContext schema(String name) {
        return JsonPath.parse(JSON.writeValueAsString(tool(name).inputSchema()));
    }

    private McpSchema.Tool tool(String name) {
        Optional<McpSchema.Tool> found = client.listTools().tools().stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst();
        assertThat(found).as("Tool %s", name).isPresent();
        return found.orElseThrow();
    }

    private static String text(McpSchema.CallToolResult response) {
        assertThat(response.content()).isNotEmpty();
        assertThat(response.content().getFirst()).isInstanceOf(McpSchema.TextContent.class);
        return ((McpSchema.TextContent) response.content().getFirst()).text();
    }
}
