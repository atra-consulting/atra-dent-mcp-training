package consulting.atra.agenten.a2a.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentCatalogTest {

    @Test
    void anUnreachableUrlLeavesNoCatalogEntryAndDoesNotAbortTheStart() throws IOException {
        String freePort = freePort();

        SubagentCatalog catalog = new SubagentCatalog(List.of(Subagent.at(freePort)));

        assertThat(catalog.names()).isEmpty();
        assertThat(catalog.card("irgendein Agent")).isNull();
    }

    @Test
    void descriptionsOnAnEmptyCatalogNameTheHintText() {
        SubagentCatalog catalog = new SubagentCatalog(List.<Subagent>of());

        assertThat(catalog.descriptions()).isEqualTo("Derzeit ist kein Fachagent erreichbar.");
    }

    @Test
    void theCardLoadsLateOnceTheSubagentBecomesReachable() throws IOException {
        String basisUrl = "http://localhost:" + freePortNumber();

        SubagentCatalog catalog = new SubagentCatalog(List.of(Subagent.at(basisUrl)),
                Duration.ofMillis(20), AgentCardFetcher.REQUEST_TIMEOUT);
        assertThat(catalog.card("Testagent")).isNull();

        HttpServer stub = stubServer(basisUrl);
        try {
            var card = pollUntilFound(catalog, Duration.ofSeconds(2));
            assertThat(card).isNotNull();
            assertThat(catalog.names()).containsExactly("Testagent");
        } finally {
            stub.stop(0);
        }
    }

    @Test
    void aHangingServerBlocksNeitherTheConstructorNorParallelReadersIndefinitely() throws Exception {
        try (ServerSocket haengend = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                while (!haengend.isClosed()) {
                    try {
                        Socket connection = haengend.accept();
                        assertThat(connection).isNotNull();
                    } catch (IOException geschlossen) {
                        return;
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            String basisUrl = "http://localhost:" + haengend.getLocalPort();

            long start = System.nanoTime();
            SubagentCatalog catalog = new SubagentCatalog(List.of(Subagent.at(basisUrl)));
            long konstruktionsdauerMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(konstruktionsdauerMs)
                    .as("Konstruktor haengt trotz nie antwortendem Subagent nicht unbegrenzt")
                    .isLessThan(9000);
            assertThat(catalog.names()).isEmpty();

            CompletableFuture<Set<String>> parallelRead =
                    CompletableFuture.supplyAsync(catalog::names);
            assertThat(parallelRead.get(1, TimeUnit.SECONDS))
                    .as("namen() blockiert nicht auf einem fremden Netzaufruf")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("every agent gets the key of its own catalog entry, and no other")
    void theKeyStaysWithTheEntryItWasConfiguredOn() throws IOException {
        String keyed = "http://localhost:" + freePortNumber();
        String unkeyed = "http://localhost:" + freePortNumber();

        HttpServer geschuetzt = stubServer(keyed, "Geschuetzter Agent");
        HttpServer offen = stubServer(unkeyed, "Offener Agent");
        try {
            SubagentCatalog catalog = new SubagentCatalog(
                    List.of(new Subagent(keyed, "geheim"), Subagent.at(unkeyed)));

            assertThat(catalog.names()).containsExactlyInAnyOrder(
                    "Geschuetzter Agent", "Offener Agent");
            assertThat(catalog.apiKey("Geschuetzter Agent")).isEqualTo("geheim");
            assertThat(catalog.apiKey("Offener Agent")).isEmpty();
            assertThat(catalog.apiKey("Nie entdeckter Agent")).isEmpty();
        } finally {
            geschuetzt.stop(0);
            offen.stop(0);
        }
    }

    private static io.a2a.spec.AgentCard pollUntilFound(SubagentCatalog catalog, Duration timeout)
            throws IOException {
        long ende = System.nanoTime() + timeout.toNanos();
        io.a2a.spec.AgentCard card = null;
        while (card == null && System.nanoTime() < ende) {
            card = catalog.card("Testagent");
            if (card == null) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException unterbrochen) {
                    Thread.currentThread().interrupt();
                    throw new IOException("unterbrochen", unterbrochen);
                }
            }
        }
        return card;
    }

    private static String freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort();
        }
    }

    private static int freePortNumber() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpServer stubServer(String basisUrl) throws IOException {
        return stubServer(basisUrl, "Testagent");
    }

    private static HttpServer stubServer(String basisUrl, String name) throws IOException {
        int port = Integer.parseInt(basisUrl.substring(basisUrl.lastIndexOf(':') + 1));
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/.well-known/agent-card.json", exchange -> {
            String card = """
                    {"name":"%s","description":"Nur fuer den Nachlade-Test.",
                     "version":"1.0.0","url":"%s/","protocolVersion":"0.3.0",
                     "capabilities":{"streaming":false,"pushNotifications":false},
                     "defaultInputModes":["text/plain"],"defaultOutputModes":["text/plain"],
                     "skills":[]}
                    """.formatted(name, basisUrl);
            byte[] body = card.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var ausgabe = exchange.getResponseBody()) {
                ausgabe.write(body);
            }
        });
        server.start();
        return server;
    }
}
