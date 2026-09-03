package consulting.atra.wissen.mcp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import consulting.atra.wissen.api.TestEmbedding;
import consulting.atra.wissen.beratung.AngerateneBehandlung;
import consulting.atra.wissen.beratung.BeratungRequest;
import consulting.atra.wissen.beratung.BeratungsResult;
import consulting.atra.wissen.beratung.Empfehlung;
import consulting.atra.wissen.beratung.Tarifempfehlung;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.model.embedding=none")
@Import(TestEmbedding.class)
class McpEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GozPruefung pruefung;

    @Autowired
    private Tarifempfehlung recommendation;

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
    @DisplayName("the server announces its name and offers exactly the five tools")
    void listTools() {
        assertThat(client.getServerInfo().name()).isEqualTo("atra-dent-wissen");

        List<McpSchema.Tool> tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                "bedingungen_suchen", "goz_pruefen",
                "beratungsleitfaden_suchen", "tarifempfehlung",
                "tarife_vergleichen");
        assertThat(tools).allSatisfy(tool ->
                assertThat(tool.description()).isNotBlank());
    }

    @Test
    @DisplayName("every tool declares itself read-only -- the Wissensdienst answers, it never writes")
    void everyToolIsAnnotatedReadOnly() {
        assertThat(client.listTools().tools()).allSatisfy(tool -> {
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
    @DisplayName("the server instructions distinguish the three kinds of knowledge")
    void instructionsSeparateTheThreeKinds() {
        String instructions = client.getServerInstructions();

        assertThat(instructions)
                .contains("veroeffentlichten Bedingungswerken")
                .contains("zitierfaehig")
                .contains("internem Steuerungswissen")
                .contains("nicht zitiert")
                .contains("dem Bedingungswerk weicht");

        assertThat(instructions)
                .contains("tarife_vergleichen")
                .contains("Produktmodell")
                .contains("keine Fundstelle");
    }

    @Test
    @DisplayName("beratungsleitfaden_suchen over MCP returns sections of the Handbuch")
    void searchBeratungsleitfadenOverHttp() {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("beratungsleitfaden_suchen")
                .arguments(Map.of("frage", "Der Kunde sagt, es sei zu teuer", "anzahl", 3))
                .build());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        DocumentContext read = JsonPath.parse(text(result));

        assertThat(read.<String>read("$.vertraulichkeit")).isEqualTo("intern");
        assertThat(read.<List<String>>read("$.abschnitte[*].dokumentId"))
                .hasSize(3).containsOnly("atra-dent-beratungshandbuch");
    }

    @Test
    @DisplayName("tarifempfehlung over MCP returns the same Empfehlung as the domain logic")
    void tarifempfehlungOverHttp() {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("tarifempfehlung")
                .arguments(Map.of("alter", 67, "angerateneBehandlung", "KEINE", "fehlendeZaehne", 0))
                .build());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        DocumentContext read = JsonPath.parse(text(result));

        BeratungsResult expected = recommendation.recommend(new BeratungRequest(
                67, List.of(), AngerateneBehandlung.KEINE, 0, null));
        assertThat(read.<String>read("$.weg")).isEqualTo(expected.weg().name());
        assertThat(read.<List<String>>read("$.empfehlungen[*].tarifschluessel"))
                .containsExactlyElementsOf(expected.empfehlungen().stream()
                        .map(Empfehlung::tarifschluessel).toList());
        assertThat(read.<List<String>>read("$.ausgeschlossene[*].kriterium"))
                .containsOnly("EINTRITTSALTER");
        assertThat(read.<String>read("$.vertraulichkeit")).isEqualTo("intern");
    }

    @Test
    @DisplayName("goz_pruefen over MCP returns the same Befunde as the domain logic")
    void checkGozOverHttp() {
        List<String> numbers = List.of("9010", "0010", "9999");

        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("goz_pruefen")
                .arguments(Map.of("tarif", "ATRA_DENT_B", "nummern", numbers))
                .build());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        DocumentContext read = JsonPath.parse(text(result));

        List<GozBefund> expected = pruefung.check("ATRA_DENT_B", numbers);
        assertThat(read.<String>read("$.tarif")).isEqualTo("ATRA_DENT_B");
        assertThat(read.<List<String>>read("$.befunde[*].status"))
                .containsExactlyElementsOf(expected.stream().map(befund -> befund.status().name()).toList());
        assertThat(read.<List<String>>read("$.befunde[*].begruendung"))
                .containsExactlyElementsOf(expected.stream().map(GozBefund::begruendung).toList());
    }

    @Test
    @DisplayName("bedingungen_suchen over MCP returns Fundstellen with an anchor")
    void searchBedingungenOverHttp() {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("bedingungen_suchen")
                .arguments(Map.of("tarif", "ATRA_DENT_S", "frage", "Wie lange ist die Wartezeit?", "anzahl", 2))
                .build());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        DocumentContext read = JsonPath.parse(text(result));

        assertThat(read.<String>read("$.tarif")).isEqualTo("ATRA_DENT_S");
        List<String> htmlUrls = read.read("$.treffer[*].htmlUrl");
        assertThat(htmlUrls).hasSize(2)
                .allSatisfy(url -> assertThat(url).startsWith("/dokumente/").contains("/html#"));
    }

    @Test
    @DisplayName("tarife_vergleichen over MCP puts the Tarife side by side and names its Bedingungswerk")
    void compareTarifeOverHttp() {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("tarife_vergleichen")
                .arguments(Map.of("tarife", List.of("ATRA_DENT_X", "ATRA_DENT_S"),
                        "leistungsbereiche", List.of("PZR", "IMP")))
                .build());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        DocumentContext read = JsonPath.parse(text(result));

        assertThat(read.<List<String>>read("$.tarife[*].schluessel"))
                .containsExactly("ATRA_DENT_S", "ATRA_DENT_X");
        assertThat(read.<List<String>>read("$.leistungsbereiche[*].schluessel"))
                .containsExactly("IMP", "PZR");
        assertThat(read.<List<String>>read("$.tarife[*].bedingungswerk"))
                .containsExactly("atra-dent-smart-avb", "atra-dent-brillant-avb");
    }

    @Test
    @DisplayName("the description of tarife_vergleichen says the result is no Beleg")
    void aComparisonIsNoBeleg() {
        String description = client.listTools().tools().stream()
                .filter(tool -> tool.name().equals("tarife_vergleichen"))
                .map(McpSchema.Tool::description)
                .findFirst()
                .orElseThrow();

        assertThat(description)
                .contains("KEIN BELEG")
                .contains("bedingungen_suchen");
    }

    @Test
    @DisplayName("an unknown Tarif in the comparison comes back as an error and not as an empty column")
    void unknownTarifInTheComparison() {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("tarife_vergleichen")
                .arguments(Map.of("tarife", List.of("ATRA_DENT_XXL")))
                .build());

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("a missing Tarif comes back as an error and not as an invented answer")
    void withoutTarif() {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest
                .builder("goz_pruefen")
                .arguments(Map.of("nummern", List.of("9010")))
                .build());

        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
        assertThat(text(result)).contains("tarif");
    }

    private static String text(McpSchema.CallToolResult ergebnis) {
        assertThat(ergebnis.content()).isNotEmpty();
        assertThat(ergebnis.content().getFirst()).isInstanceOf(McpSchema.TextContent.class);
        return ((McpSchema.TextContent) ergebnis.content().getFirst()).text();
    }
}
