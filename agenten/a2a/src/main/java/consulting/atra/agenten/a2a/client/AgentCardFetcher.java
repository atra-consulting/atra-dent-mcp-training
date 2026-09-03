package consulting.atra.agenten.a2a.client;

import io.a2a.spec.AgentCard;
import io.a2a.util.Utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class AgentCardFetcher {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    public static final String CARD_PATH = "/.well-known/agent-card.json";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private AgentCardFetcher() {
    }

    public static AgentCard fetch(String basisUrl) {
        return fetch(basisUrl, REQUEST_TIMEOUT);
    }

    public static AgentCard fetch(String basisUrl, Duration requestTimeout) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(basisUrl + CARD_PATH))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException unterbrochen) {
            Thread.currentThread().interrupt();
            throw new AgentUnreachableException(basisUrl,
                    "Der Abruf der Agent Card wurde unterbrochen", unterbrochen);
        } catch (IOException | RuntimeException error) {
            throw new AgentUnreachableException(basisUrl,
                    "Die Agent Card ist nicht abrufbar: " + error.getMessage(), error);
        }
        if (response.statusCode() != 200) {
            throw new AgentUnreachableException(basisUrl,
                    "HTTP " + response.statusCode() + " beim Abruf der Agent Card");
        }
        try {
            return Utils.OBJECT_MAPPER.readValue(response.body(), AgentCard.class);
        } catch (IOException | RuntimeException unreadable) {
            throw new AgentUnreachableException(basisUrl,
                    "Die Agent Card ist nicht lesbar: " + unreadable.getMessage(), unreadable);
        }
    }
}
