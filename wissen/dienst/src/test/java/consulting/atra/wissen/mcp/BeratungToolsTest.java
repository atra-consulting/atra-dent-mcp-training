package consulting.atra.wissen.mcp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import consulting.atra.wissen.api.TestEmbedding;
import consulting.atra.wissen.beratung.AngerateneBehandlung;
import consulting.atra.wissen.beratung.ExcludedTarif;
import consulting.atra.wissen.beratung.BeratungRequest;
import consulting.atra.wissen.beratung.BeratungsResult;
import consulting.atra.wissen.beratung.Empfehlung;
import consulting.atra.wissen.beratung.Hinweis;
import consulting.atra.wissen.beratung.Tarifempfehlung;
import consulting.atra.wissen.beratung.Vorversicherung;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class BeratungToolsTest {

    private static final String HANDBOOK = "atra-dent-beratungshandbuch";
    private static final JsonMapper JSON = new JsonMapper();

    @LocalServerPort
    private int port;

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
    @DisplayName("five tools are registered, the two Beratung tools under their names")
    void toolsAreRegistered() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "bedingungen_suchen", "goz_pruefen",
                "beratungsleitfaden_suchen", "tarifempfehlung",
                "tarife_vergleichen");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"beratungsleitfaden_suchen", "tarifempfehlung"})
    @DisplayName("both descriptions say what kind of knowledge comes out here")
    void theDescriptionCarriesTheVertraulichkeit(String name) {
        String description = tool(name).description();

        assertThat(description)
                .contains("INTERNES STEUERUNGSWISSEN")
                .contains("führt den Berater")
                .contains("NICHT ZUR VORLAGE BEIM KUNDEN")
                .contains("gegenüber dem Kunden nicht zitiert")
                .contains("gilt ohne Ausnahme das Bedingungswerk")
                .contains("Bedingungsstelle zu nennen")
                .contains("bedingungen_suchen");
    }

    @Test
    @DisplayName("the description of tarifempfehlung puts the Bedarf above the order")
    void theDescriptionPutsTheBedarfFirst() {
        String description = tool("tarifempfehlung").description();

        assertThat(description)
                .contains("DER BEDARF GEHT VOR")
                .contains("ausschließlich bei gleichwertiger Eignung")
                .contains("keine Vertriebsvorgabe")
                .contains("nennt keine Beiträge und rechnet keine Erstattung");
    }

    @Test
    @DisplayName("beratungsleitfaden_suchen knows no Tarif and requires the question")
    void searchWithoutTarif() {
        DocumentContext schema = schema("beratungsleitfaden_suchen");

        List<String> mandatory = schema.read("$.required");
        assertThat(mandatory).containsExactly("frage");
        assertThat(schema.<Map<String, Object>>read("$.properties"))
                .containsOnlyKeys("frage", "anzahl");
    }

    @Test
    @DisplayName("tarifempfehlung requires the age and lists the three-valued options")
    void recommendationSchema() {
        DocumentContext schema = schema("tarifempfehlung");

        assertThat(schema.<List<String>>read("$.required")).containsExactly("alter");
        assertThat(schema.<Map<String, Object>>read("$.properties")).containsOnlyKeys(
                "alter", "behandlungsschwerpunkte", "angerateneBehandlung",
                "fehlendeZaehne", "vorversicherung");

        assertThat(schema.<List<String>>read("$.properties.angerateneBehandlung.enum"))
                .containsExactlyInAnyOrder("KEINE", "ANGERATEN", "NICHT_ERHOBEN");
        assertThat(schema.<List<String>>read("$.properties.vorversicherung.enum"))
                .containsExactlyInAnyOrder("KEINE", "LUECKENLOS", "NICHT_ERHOBEN");
    }

    @Test
    @DisplayName("tarifempfehlung returns the same as calling the domain logic directly")
    void tarifempfehlungCarriesTheDomainLogic() {
        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("tarifempfehlung")
                .arguments(Map.of(
                        "alter", 45, "behandlungsschwerpunkte", List.of("IMP"),
                        "angerateneBehandlung", "KEINE", "fehlendeZaehne", 2,
                        "vorversicherung", "LUECKENLOS"))
                .build());
        assertThat(response.isError()).isNotEqualTo(Boolean.TRUE);

        BeratungsResult expected = recommendation.recommend(new BeratungRequest(
                45, List.of("IMP"), AngerateneBehandlung.KEINE, 2, Vorversicherung.LUECKENLOS));

        DocumentContext read = JsonPath.parse(text(response));
        assertThat(read.<String>read("$.weg")).isEqualTo(expected.weg().name());
        assertThat(read.<String>read("$.zusammenfassung")).isEqualTo(expected.zusammenfassung());
        assertThat(read.<List<String>>read("$.empfehlungen[*].tarifschluessel"))
                .containsExactlyElementsOf(expected.empfehlungen().stream()
                        .map(Empfehlung::tarifschluessel).toList());
        assertThat(read.<List<Integer>>read("$.empfehlungen[*].rang"))
                .containsExactlyElementsOf(expected.empfehlungen().stream()
                        .map(Empfehlung::rang).toList());
        assertThat(read.<List<String>>read("$.empfehlungen[*].begruendung"))
                .containsExactlyElementsOf(expected.empfehlungen().stream()
                        .map(Empfehlung::begruendung).toList());
        assertThat(read.<List<String>>read("$.ausgeschlossene[*].tarifschluessel"))
                .containsExactlyElementsOf(expected.ausgeschlossene().stream()
                        .map(ExcludedTarif::tarifschluessel).toList());
        assertThat(read.<List<String>>read("$.hinweise[*].schluessel"))
                .containsExactlyElementsOf(expected.hinweise().stream()
                        .map(Hinweis::schluessel).toList());

        assertThat(read.<String>read("$.grundsatz.vorrang"))
                .isEqualTo(expected.grundsatz().vorrang());
        assertThat(read.<List<String>>read("$.complianceGrenzen[*].schluessel")).isNotEmpty();
        assertThat(read.<String>read("$.vertraulichkeit")).isEqualTo("intern");
        assertThat(read.<Integer>read("$.anliegen.alter")).isEqualTo(45);
        assertThat(read.<List<String>>read("$.anliegen.behandlungsschwerpunkte"))
                .containsExactly("IMP");
    }

    @Test
    @DisplayName("tarifempfehlung without an age is an error and no invented answer")
    void tarifempfehlungWithoutAge() {
        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("tarifempfehlung")
                .arguments(Map.of("behandlungsschwerpunkte", List.of("ZE")))
                .build());

        assertThat(response.isError()).isEqualTo(Boolean.TRUE);
        assertThat(text(response)).contains("alter");
    }

    @Test
    @DisplayName("beratungsleitfaden_suchen returns sections of the Handbuch with the note")
    void searchReturnsHandbuchSections() {
        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("beratungsleitfaden_suchen")
                .arguments(Map.of("frage", "Der Kunde sagt, es sei zu teuer", "anzahl", 3))
                .build());
        assertThat(response.isError()).isNotEqualTo(Boolean.TRUE);

        DocumentContext read = JsonPath.parse(text(response));
        assertThat(read.<String>read("$.frage")).isEqualTo("Der Kunde sagt, es sei zu teuer");
        assertThat(read.<String>read("$.vertraulichkeit")).isEqualTo("intern");
        assertThat(read.<String>read("$.vertraulichkeitshinweis"))
                .contains("INTERNES STEUERUNGSWISSEN")
                .contains("gilt ohne Ausnahme das Bedingungswerk");

        assertThat(read.<List<String>>read("$.abschnitte[*].dokumentId"))
                .hasSize(3).containsOnly(HANDBOOK);
        assertThat(read.<List<String>>read("$.abschnitte[*].abschnittId"))
                .doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNotBlank());
        assertThat(read.<List<String>>read("$.abschnitte[*].ueberschrift"))
                .allSatisfy(ueberschrift -> assertThat(ueberschrift).isNotBlank());
    }

    @Test
    @DisplayName("without anzahl five sections apply")
    void searchWithoutAnzahl() {
        McpSchema.CallToolResult response = client.callTool(McpSchema.CallToolRequest
                .builder("beratungsleitfaden_suchen")
                .arguments(Map.of("frage", "Wie steige ich ins Gespräch ein?"))
                .build());
        assertThat(response.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(JsonPath.parse(text(response)).<List<String>>read("$.abschnitte[*].abschnittId"))
                .hasSize(5);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Wie lange ist die Wartezeit?", "Wie hoch ist der Selbstbehalt?"})
    @DisplayName("the separation holds across the tools too, in both directions")
    void toolsSeparateTheSearchSpaces(String question) {
        McpSchema.CallToolResult fromHandbuch = client.callTool(McpSchema.CallToolRequest
                .builder("beratungsleitfaden_suchen")
                .arguments(Map.of("frage", question, "anzahl", 20))
                .build());
        assertThat(JsonPath.parse(text(fromHandbuch)).<List<String>>read("$.abschnitte[*].dokumentId"))
                .isNotEmpty().containsOnly(HANDBOOK);

        McpSchema.CallToolResult fromBedingungen = client.callTool(McpSchema.CallToolRequest
                .builder("bedingungen_suchen")
                .arguments(Map.of("tarif", "ATRA_DENT_X", "frage", question, "anzahl", 20))
                .build());
        assertThat(JsonPath.parse(text(fromBedingungen)).<List<String>>read("$.treffer[*].dokumentId"))
                .isNotEmpty().doesNotContain(HANDBOOK);
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
