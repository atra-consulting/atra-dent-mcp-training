package consulting.atra.agenten.schadensfall;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import consulting.atra.agenten.a2a.server.Headers;
import consulting.atra.agenten.schadensfall.agent.SchadensfallPoller;
import consulting.atra.agenten.schadensfall.kernsystem.KernsystemClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {TestModel.WITHOUT_API_KEY, "schadensfall.poll-enabled=false"})
@Import({TestModel.class, TestTools.class})
class A2aEndpointTest {

    private static final Configuration SETTING = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @MockitoBean
    private KernsystemClient kernsystem;

    @Autowired
    private ApplicationContext context;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @BeforeEach
    void prepare() {
        when(kernsystem.fall(50071)).thenReturn(Optional.of(fall("eingereicht")));
        when(kernsystem.claim(50071)).thenReturn(true);
    }

    @Test
    @DisplayName("the Agent Card is retrievable at the well-known path and reports streaming per A2A 0.3")
    void theCardIsRetrievableAndReportsStreaming() throws Exception {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(basis() + "/.well-known/agent-card.json")).GET().build();
        HttpResponse<String> answer = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(answer.statusCode()).isEqualTo(200);
        DocumentContext card = JsonPath.using(SETTING).parse(answer.body());
        assertThat(card.<String>read("$.name")).contains("Schadensfallagent");
        assertThat(card.<Boolean>read("$.capabilities.streaming")).isTrue();
        assertThat(card.<String>read("$.protocolVersion")).isEqualTo("0.3.0");
        assertThat(card.<String>read("$.skills[0].id")).isEqualTo("fall_pruefen");
    }

    @Test
    @DisplayName("without a Schadensfall number the agent asks back -- input-required and no refusal")
    void withoutASchadensfallNumberInputRequired() throws Exception {
        DocumentContext answer = messageSend("Hallo");

        assertThat(answer.<Object>read("$.error")).isNull();
        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("input-required");
        assertThat(answer.<String>read("$.result.status.message.parts[0].text"))
                .contains("Fallnummer");
    }

    @Test
    @DisplayName("with a Schadensfall number the check runs through -- completed with the artifact bewertung")
    void withASchadensfallNumberBewertung() throws Exception {
        DocumentContext answer = messageSend("Bitte pruefe Fall 50071.");

        assertThat(answer.<Object>read("$.error")).isNull();
        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("completed");
        assertThat(answer.<String>read("$.result.artifacts[0].name")).isEqualTo("bewertung");
        assertThat(answer.<String>read("$.result.artifacts[0].parts[0].text")).contains("50071");
        assertThat(answer.<String>read("$.result.artifacts[0].parts[1].data.bewertung.empfehlung"))
                .isEqualTo("eskalation");
        assertThat(answer.<Object>read("$.result.artifacts[0].parts[1].data.protokoll"))
                .isNotNull();
    }

    @Test
    @DisplayName("a paid-out Schadensfall is refused -- rejected, no second Bewertung")
    void aPaidOutSchadensfallIsRejected() throws Exception {
        when(kernsystem.fall(50071)).thenReturn(Optional.of(fall("ausgezahlt")));

        DocumentContext answer = messageSend("Bitte pruefe Fall 50071.");

        assertThat(answer.<Object>read("$.error")).isNull();
        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("rejected");
        assertThat(answer.<String>read("$.result.status.message.parts[0].text"))
                .contains("ausgezahlt");
    }

    @Test
    @DisplayName("without x-kunden-id the skill ends rejected -- without an artifact")
    void withoutTheHeaderRejected() throws Exception {
        DocumentContext answer = messageSend("Bitte pruefe Fall 50071.", null);

        assertThat(answer.<Object>read("$.error")).isNull();
        assertThat(answer.<List<Object>>read("$.result.artifacts")).isNullOrEmpty();
        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("rejected");
    }

    @Test
    @DisplayName("the refusal without the header is verbatim the one for a foreign Schadensfall")
    void aRefusalWithoutTheHeaderIsTheOneForAForeignSchadensfall() throws Exception {
        String withoutHeader = messageSend("Bitte pruefe Fall 50071.", null)
                .read("$.result.status.message.parts[0].text");
        String fremd = messageSend("Bitte pruefe Fall 50071.", "4712")
                .read("$.result.status.message.parts[0].text");

        assertThat(withoutHeader).isEqualTo(fremd);
    }

    @Test
    @DisplayName("with schadensfall.poll-enabled=false there is no poller")
    void withoutTheSwitchNoPoller() {
        assertThat(context.getBeanNamesForType(SchadensfallPoller.class)).isEmpty();
    }


    private static JsonNode fall(String status) {
        return IMAGES.readTree("""
                {"id":50071,"kundenId":4711,"status":"%s","behandlungsdatum":"2026-01-08",
                 "rechnungsbetrag":"395.00",
                 "positionen":[{"goz":"2197","betrag":"25.00"},{"goz":"2150","betrag":"370.00"}],
                 "rechnung":{"patient":"Anna Mueller"}}
                """.formatted(status));
    }

    private DocumentContext messageSend(String text) throws Exception {
        return messageSend(text, "4711");
    }

    private DocumentContext messageSend(String text, String kundenId) throws Exception {
        HttpRequest.Builder build = HttpRequest.newBuilder(URI.create(basis() + "/"))
                .header("Content-Type", "application/json");
        if (kundenId != null) {
            build.header(Headers.HEADER, kundenId);
        }
        HttpRequest request = build
                .POST(HttpRequest.BodyPublishers.ofString(message("message/send", text)))
                .build();
        HttpResponse<String> answer = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(answer.statusCode()).isEqualTo(200);
        return JsonPath.using(SETTING).parse(answer.body());
    }

    private String basis() {
        return "http://localhost:" + port;
    }

    private static String message(String methode, String text) {
        return """
                {"jsonrpc": "2.0", "id": "1", "method": "%s",
                 "params": {"message": {"kind": "message", "messageId": "%s", "role": "user",
                             "parts": [{"kind": "text", "text": "%s"}]}}}
                """.formatted(methode, UUID.randomUUID(), text.replace("\"", "\\\""));
    }
}
