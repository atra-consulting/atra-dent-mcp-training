package consulting.atra.agenten.orchestrator;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.client.SdkAgentClient;
import consulting.atra.agenten.a2a.client.SubagentResponse;
import consulting.atra.agenten.mcp.McpProperties;
import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.google.genai.api-key=test",
            "agenten.subagents.catalog.testagent.url=http://localhost:1"
        })
@Import(TestToolSource.class)
class OrchestratorEndpointTest {

    private static final Configuration SETTING = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("the context boots with a dummy key, serves the real card and reaches only the Kernsystem")
    void theContextBootsAndTheCardIsRetrievable() throws Exception {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(basis() + "/.well-known/agent-card.json")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        DocumentContext card = JsonPath.using(SETTING).parse(response.body());
        assertThat(card.<String>read("$.name")).isEqualTo("atra.dent Orchestrator");
        assertThat(card.<Boolean>read("$.capabilities.streaming")).isTrue();
        assertThat(card.<String>read("$.protocolVersion")).isEqualTo("0.3.0");

        assertThat(context).isNotNull();
        McpProperties mcp = context.getBean(McpProperties.class);
        assertThat(mcp.kernsystem().url()).isNotBlank();
        assertThat(mcp.rechenkern()).isNull();
        assertThat(mcp.wissen()).isNull();
    }

    @Test
    @DisplayName("SdkAgentClient carries x-kunden-id over the real wire to a fake subagent and returns COMPLETED")
    void theMandantHopCarriesTheKundennummerOverTheRealWire() throws Exception {
        try (FakeSubagent fake = new FakeSubagent()) {
            AgentCard card = A2A.getAgentCard(fake.basisUrl(), "/.well-known/agent-card.json", null);

            SubagentResponse response = new SdkAgentClient("Fake-Subagent", "orchestrator", card, Duration.ofSeconds(10))
                    .send(null, null, "Anliegen", Map.of(), 10001L, status -> { });

            assertThat(fake.receivedKundenId()).isEqualTo("10001");
            assertThat(response.outcome()).isEqualTo(Outcome.COMPLETED);
            assertThat(response.text()).contains("Fake-Antwort vom Subagenten");
        }
    }

    @Test
    @DisplayName("SdkAgentClient carries the Beleg as the data part rechnung over the real wire")
    void theBelegTravelsAsADataPartOverTheRealWire() throws Exception {
        try (FakeSubagent fake = new FakeSubagent()) {
            AgentCard card = A2A.getAgentCard(fake.basisUrl(), "/.well-known/agent-card.json", null);
            Map<String, Object> data = Map.of("rechnung", Map.of("rechnungsnummer", "2026-4711"));

            new SdkAgentClient("Fake-Subagent", "orchestrator", card, Duration.ofSeconds(10))
                    .send(null, null, "Rechnung einreichen", data, 10001L, status -> { });

            DocumentContext body = JsonPath.using(SETTING).parse(fake.lastBody());
            List<String> rechnungNumbers = body.read(
                    "$.params.message.parts[?(@.kind=='data')].data.rechnung.rechnungsnummer");
            assertThat(rechnungNumbers).containsExactly("2026-4711");
        }
    }

    private String basis() {
        return "http://localhost:" + port;
    }

    private static final class FakeSubagent implements AutoCloseable {

        private final HttpServer server;
        private final int port;
        private volatile String empfangeneKundenId;
        private volatile String letzterRumpf;

        FakeSubagent() throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            port = server.getAddress().getPort();
            server.createContext("/.well-known/agent-card.json", this::card);
            server.createContext("/", this::message);
            server.setExecutor(null);
            server.start();
        }

        String basisUrl() {
            return "http://localhost:" + port;
        }

        String receivedKundenId() {
            return empfangeneKundenId;
        }

        String lastBody() {
            return letzterRumpf;
        }

        private void card(HttpExchange austausch) throws IOException {
            String card = """
                    {"name":"Fake-Subagent","description":"Nur fuer den Mandanten-Sprung-Test.",
                     "version":"1.0.0","url":"%s/","protocolVersion":"0.3.0",
                     "capabilities":{"streaming":false,"pushNotifications":false},
                     "defaultInputModes":["text/plain"],"defaultOutputModes":["text/plain"],
                     "skills":[]}
                    """.formatted(basisUrl());
            respond(austausch, 200, card);
        }

        private void message(HttpExchange austausch) throws IOException {
            if (!"/".equals(austausch.getRequestURI().getPath())) {
                austausch.sendResponseHeaders(404, -1);
                return;
            }
            empfangeneKundenId = austausch.getRequestHeaders().getFirst("x-kunden-id");
            letzterRumpf = new String(austausch.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String response = """
                    {"jsonrpc":"2.0","id":"1",
                     "result":{"kind":"task","id":"task-fake","contextId":"kontext-fake",
                       "status":{"state":"completed"},
                       "artifacts":[{"artifactId":"a1","name":"response",
                         "parts":[{"kind":"text","text":"Fake-Antwort vom Subagenten"}]}]}}
                    """;
            respond(austausch, 200, response);
        }

        private static void respond(HttpExchange austausch, int status, String rumpfText) throws IOException {
            byte[] body = rumpfText.getBytes(StandardCharsets.UTF_8);
            austausch.getResponseHeaders().add("Content-Type", "application/json");
            austausch.sendResponseHeaders(status, body.length);
            try (var ausgabe = austausch.getResponseBody()) {
                ausgabe.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
