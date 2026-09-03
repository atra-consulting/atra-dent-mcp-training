package consulting.atra.agenten.beratung;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestModel.WITHOUT_API_KEY)
@Import({TestModel.class, TestTools.class})
class BeratungServiceTest {

    private static final Configuration SETTING = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    @LocalServerPort
    private int port;

    @Autowired
    private TestModel.Aufrufprotokoll protokoll;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("the real Agent Card is loaded and promises four skills")
    void theCardIsLoaded() throws Exception {
        DocumentContext card = card();

        assertThat(card.<String>read("$.name")).isEqualTo("atra.dent Beratungsagent");
        assertThat(card.<List<String>>read("$.skills[*].id"))
                .containsExactly("bestandsberatung", "neuberatung", "rechnung_einreichen",
                        "kontaktdaten_aendern");
        assertThat(card.<Boolean>read("$.capabilities.streaming")).isTrue();
        assertThat(card.<Boolean>read("$.capabilities.pushNotifications")).isFalse();
    }

    @Test
    @DisplayName("the card promises what the agent does not do - and names the Beleg")
    void theCardNamesLimitsAndBelege() throws Exception {
        String description = card().read("$.description");

        assertThat(description)
                .contains("schliesst nichts ab")
                .contains("x-kunden-id")
                .contains("rechnet keine Erstattung im Einzelfall")
                .contains("input-required")
                .contains("belege");

        assertThat(card().<String>read("$.skills[0].description"))
                .contains("MIT laufendem Vertrag")
                .contains("nachgeschlagen und nicht erfragt");
        assertThat(card().<String>read("$.skills[1].description"))
                .contains("OHNE Akte")
                .contains("erfragt werden");
    }

    @Test
    @DisplayName("an A2A turn runs through and delivers the artifact beratung")
    void a2aTurnDeliversArtifact() throws Exception {
        DocumentContext answer = send("Lohnt sich ein Wechsel?", 10001L);

        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("completed");
        assertThat(answer.<List<Object>>read("$.result.artifacts")).hasSize(1);
        assertThat(answer.<String>read("$.result.artifacts[0].name")).isEqualTo("beratung");

        assertThat(answer.<List<Object>>read("$.result.artifacts[0].parts")).hasSize(2);
        assertThat(answer.<Object>read("$.result.artifacts[0].parts[1].data").toString())
                .contains("belege").contains("hinweise");
    }

    @Test
    @DisplayName("the service answers without a Kundennummer too - an Interessent without an Akte")
    void respondsEvenWithoutAKundennummer() throws Exception {
        DocumentContext answer = send("Welche Tarife gibt es?", null);

        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("completed");
    }

    @Test
    @DisplayName("the answer comes from the tool loop, not from the service")
    void theResponseComesFromTheLoop() throws Exception {
        DocumentContext answer = send("Was zahlt mein Tarif bei Implantaten?", 10001L);

        String text = answer.read("$.result.artifacts[0].parts[0].text");

        assertThat(text).isEqualTo(TestModel.ANSWER);
    }

    @Test
    @DisplayName("the model is offered exactly the allowed tools")
    void theModelGetsOnlyTheAllowedTools() throws Exception {
        send("Was zahlt mein Tarif bei Implantaten?", 10001L);
        List<String> loggedIn = protokoll.last().tools();

        send("Welche Tarife gibt es?", null);
        List<String> open = protokoll.last().tools();

        assertThat(loggedIn).hasSize(14).contains("meine_kontaktdaten_aendern");
        assertThat(open).hasSize(10)
                .doesNotContain("mein_vertrag_lesen", "mein_beitrag_berechnen",
                        "meine_schadensfaelle_auflisten", "meine_kontaktdaten_aendern");
        assertThat(loggedIn).doesNotContain("meinen_tarif_wechseln",
                "schadensfall_einreichen", "schadensfall_lesen");
    }

    @Test
    @DisplayName("the strong model answers, not the fast one")
    void theStrongModelAnswers() throws Exception {
        send("Was zahlt mein Tarif bei Implantaten?", 10001L);

        assertThat(protokoll.last().modell()).isEqualTo("gemini-3.6-flash");
    }


    private DocumentContext card() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(basis() + "/.well-known/agent-card.json")).GET().build();
        HttpResponse<String> answer = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(answer.statusCode()).isEqualTo(200);
        return JsonPath.using(SETTING).parse(answer.body());
    }

    private DocumentContext send(String text, Long kundenId) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(basis() + "/"))
                .header("Content-Type", "application/json");
        if (kundenId != null) {
            builder.header(consulting.atra.agenten.a2a.server.Headers.HEADER,
                    String.valueOf(kundenId));
        }
        HttpResponse<String> answer = client.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(message(text))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(answer.statusCode()).isEqualTo(200);
        return JsonPath.using(SETTING).parse(answer.body());
    }

    private String basis() {
        return "http://localhost:" + port;
    }

    private static String message(String text) {
        return """
                {"jsonrpc": "2.0", "id": "1", "method": "message/send",
                 "params": {"message": {"kind": "message", "messageId": "%s", "role": "user",
                             "parts": [{"kind": "text", "text": "%s"}]}}}
                """.formatted(UUID.randomUUID(), text.replace("\"", "\\\""));
    }
}
