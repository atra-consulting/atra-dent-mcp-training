package consulting.atra.agenten.beratung;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import consulting.atra.agenten.a2a.server.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.NestedTestConfiguration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = TestModel.WITHOUT_API_KEY)
@Import({TestModel.class, TestTools.class})
class A2aEndpointTest {

    private static final Configuration SETTING = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    @LocalServerPort
    private int port;

    @Autowired
    private TestTools.ToolLog toolBook;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @BeforeEach
    void prepare() {
        toolBook.clear();
    }

    @Test
    @DisplayName("the Agent Card is retrievable at the well-known path and reports streaming per A2A 0.3")
    void theCardIsRetrievableAndReportsStreaming() throws Exception {
        HttpResponse<String> answer = get("/.well-known/agent-card.json");
        assertThat(answer.statusCode()).isEqualTo(200);

        DocumentContext card = JsonPath.using(SETTING).parse(answer.body());
        assertThat(card.<String>read("$.name")).contains("Beratungsagent");
        assertThat(card.<Boolean>read("$.capabilities.streaming")).isTrue();
        assertThat(card.<String>read("$.protocolVersion")).isEqualTo("0.3.0");
    }

    @Test
    @DisplayName("message/send with x-kunden-id reaches the domain logic -- the community controller is superseded")
    void messageSendWithTheKundenHeaderReachesTheDomainLogic() throws Exception {
        DocumentContext answer = messageSend("Was zahlt mein Tarif?", "10001");

        assertThat(answer.<Object>read("$.error")).isNull();
        assertThat(answer.<String>read("$.result.status.state")).isEqualTo("completed");

        assertThat(toolBook.kundenNumbers())
                .as("die Kundennummer aus x-kunden-id am Tool-Aufruf")
                .containsOnly(10001L);
    }

    @Test
    @DisplayName("a broken x-kunden-id header is rejected with JSON-RPC error -32600")
    void aBrokenKundenHeaderIsRejected() throws Exception {
        DocumentContext answer = messageSend("Was zahlt mein Tarif?", "quatsch");

        assertThat(answer.<Integer>read("$.error.code")).isEqualTo(-32600);
        assertThat(answer.<String>read("$.error.message")).contains(Headers.HEADER);
        assertThat(toolBook.kundenNumbers()).isEmpty();
    }

    @Test
    @DisplayName("a broken x-kunden-id header is rejected on message/stream too, not with HTTP 500")
    void aBrokenKundenHeaderIsRejectedInTheStreamToo() throws Exception {
        String body = message("message/stream", "Was zahlt mein Tarif?");
        HttpRequest request = HttpRequest.newBuilder(URI.create(basis() + "/"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header(Headers.HEADER, "quatsch")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> answer = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(answer.statusCode()).isEqualTo(200);

        List<String> dataRows = answer.body().lines()
                .filter(line -> line.startsWith("data:"))
                .toList();
        assertThat(dataRows).as("genau ein Fehler-Frame, kein normaler Event-Stream").hasSize(1);

        DocumentContext frame = JsonPath.using(SETTING)
                .parse(dataRows.getFirst().substring(5).strip());
        assertThat(frame.<Integer>read("$.error.code")).isEqualTo(-32600);
        assertThat(frame.<String>read("$.error.message")).contains(Headers.HEADER);
        assertThat(frame.<Object>read("$.result")).isNull();
        assertThat(toolBook.kundenNumbers()).isEmpty();
    }

    @Nested
    @DisplayName("Frame-Pacing")
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = TestModel.WITHOUT_API_KEY)
    @Import({StagedModel.class, TestTools.class})
    class Streaming {

        @LocalServerPort
        private int port;

        private final HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        @Test
        @DisplayName("message/stream delivers the intermediate states one by one and not buffered at the end")
        void messageStreamDeliversFramesOneByOne() throws Exception {
            String body = message("message/stream", "Lohnt sich ein Wechsel?");
            HttpRequest request = HttpRequest.newBuilder(URI.create(basis() + "/"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header(Headers.HEADER, "10001")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> answer =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertThat(answer.statusCode()).isEqualTo(200);

            List<Long> arrival = new ArrayList<>();
            List<DocumentContext> frames = new ArrayList<>();
            try (BufferedReader leser = new BufferedReader(
                    new InputStreamReader(answer.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = leser.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        arrival.add(System.nanoTime());
                        frames.add(JsonPath.using(SETTING).parse(line.substring(5).strip()));
                    }
                }
            }

            assertThat(frames).as("mindestens Status-Updates und ein Abschluss").isNotEmpty();

            List<Integer> statusIndexes = new ArrayList<>();
            for (int i = 0; i < frames.size(); i++) {
                DocumentContext frame = frames.get(i);
                if ("status-update".equals(frame.<String>read("$.result.kind"))
                        && !Boolean.TRUE.equals(frame.<Boolean>read("$.result.final"))) {
                    statusIndexes.add(i);
                }
            }
            assertThat(statusIndexes)
                    .as("mindestens zwei status-update-Frames vor dem finalen completed-Frame")
                    .hasSizeGreaterThanOrEqualTo(2);

            DocumentContext last = frames.getLast();
            assertThat(last.<String>read("$.result.kind")).isEqualTo("status-update");
            assertThat(last.<Boolean>read("$.result.final")).isTrue();
            assertThat(last.<String>read("$.result.status.state")).isEqualTo("completed");

            long spanneMs = Duration.ofNanos(arrival.getLast() - arrival.getFirst()).toMillis();
            assertThat(spanneMs)
                    .as("Abstand zwischen erstem und letztem Frame — echtes Streaming, kein Puffer")
                    .isGreaterThan(200);
        }

        private String basis() {
            return "http://localhost:" + port;
        }
    }


    private DocumentContext messageSend(String text, String kundenId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(basis() + "/"))
                .header("Content-Type", "application/json");
        if (kundenId != null) {
            builder.header(Headers.HEADER, kundenId);
        }
        HttpResponse<String> answer = post(builder, message("message/send", text));
        assertThat(answer.statusCode()).isEqualTo(200);
        return JsonPath.using(SETTING).parse(answer.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(basis() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpRequest.Builder erbauer, String koerper) throws Exception {
        return client.send(erbauer.POST(HttpRequest.BodyPublishers.ofString(koerper)).build(),
                HttpResponse.BodyHandlers.ofString());
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
