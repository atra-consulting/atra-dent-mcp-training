package consulting.atra.agenten.a2a.client;

import com.sun.net.httpserver.HttpServer;
import io.a2a.spec.AgentCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCardFetcherTest {

    @Test
    @DisplayName("a slow neighbour is reached when the caller grants it the time")
    void aLongerTimeoutOutlastsASlowStart() throws IOException {
        int port = freePort();
        HttpServer stub = slowCardServer(port, Duration.ofMillis(1200));
        try {
            assertThatThrownBy(() -> AgentCardFetcher.fetch("http://localhost:" + port,
                    Duration.ofMillis(300)))
                    .isInstanceOf(AgentUnreachableException.class);

            AgentCard card = AgentCardFetcher.fetch("http://localhost:" + port,
                    Duration.ofSeconds(5));
            assertThat(card.name()).isEqualTo("Testagent");
        } finally {
            stub.stop(0);
        }
    }

    private static HttpServer slowCardServer(int port, Duration delay) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(AgentCardFetcher.CARD_PATH, exchange -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] body = card("http://localhost:" + port).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    @DisplayName("the card comes from the well-known path")
    void theCardIsRead() throws IOException {
        int port = freePort();
        HttpServer stub = cardServer(port, 200, card("http://localhost:" + port));
        try {
            AgentCard card = AgentCardFetcher.fetch("http://localhost:" + port);

            assertThat(card.name()).isEqualTo("Testagent");
            assertThat(card.protocolVersion()).isEqualTo("0.3.0");
        } finally {
            stub.stop(0);
        }
    }

    @Test
    @DisplayName("a foreign status is no half success")
    void anUnknownStatusBecomesAnException() throws IOException {
        int port = freePort();
        HttpServer stub = cardServer(port, 404, "{}");
        try {
            assertThatThrownBy(() -> AgentCardFetcher.fetch("http://localhost:" + port))
                    .isInstanceOf(AgentUnreachableException.class)
                    .hasMessageContaining("404")
                    .hasMessageContaining("localhost:" + port);
        } finally {
            stub.stop(0);
        }
    }

    @Test
    @DisplayName("unreadable JSON is the same case as no network")
    void anUnreadableCardBecomesAnException() throws IOException {
        int port = freePort();
        HttpServer stub = cardServer(port, 200, "das ist keine Karte");
        try {
            assertThatThrownBy(() -> AgentCardFetcher.fetch("http://localhost:" + port))
                    .isInstanceOf(AgentUnreachableException.class);
        } finally {
            stub.stop(0);
        }
    }

    @Test
    @DisplayName("where nobody answers there is no card and no foreign exception")
    void noServerBecomesAnException() throws IOException {
        int port = freePort();

        assertThatThrownBy(() -> AgentCardFetcher.fetch("http://localhost:" + port))
                .isInstanceOf(AgentUnreachableException.class)
                .extracting(error -> ((AgentUnreachableException) error).agent())
                .isEqualTo("http://localhost:" + port);
    }

    @Test
    @DisplayName("an unusable address is the same case as no network")
    void anUnusableAddressBecomesAnException() {
        assertThatThrownBy(() -> AgentCardFetcher.fetch("http://kein server:8081"))
                .isInstanceOf(AgentUnreachableException.class)
                .extracting(error -> ((AgentUnreachableException) error).agent())
                .isEqualTo("http://kein server:8081");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String card(String basisUrl) {
        return """
                {"name":"Testagent","description":"Nur fuer den Abruf-Test.",
                 "version":"1.0.0","url":"%s/","protocolVersion":"0.3.0",
                 "capabilities":{"streaming":false,"pushNotifications":false},
                 "defaultInputModes":["text/plain"],"defaultOutputModes":["text/plain"],
                 "skills":[]}
                """.formatted(basisUrl);
    }

    private static HttpServer cardServer(int port, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext(AgentCardFetcher.CARD_PATH, exchange -> {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, data.length);
            try (var ausgabe = exchange.getResponseBody()) {
                ausgabe.write(data);
            }
        });
        server.start();
        return server;
    }
}
