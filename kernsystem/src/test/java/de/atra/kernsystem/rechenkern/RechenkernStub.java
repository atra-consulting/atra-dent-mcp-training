package de.atra.kernsystem.rechenkern;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RechenkernStub implements AutoCloseable {

    private static final String PATH = "/api/v1/beitragsberechnung";

    public static final String DEFAULT_BEITRAG = "{\"monatsbeitrag\":\"20.90\"}";

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String contentType = "application/json";
    private volatile String response = DEFAULT_BEITRAG;
    private volatile Duration delay = Duration.ZERO;
    private volatile boolean breaks;

    public RechenkernStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException nichtGestartet) {
            throw new UncheckedIOException(nichtGestartet);
        }
        server.createContext(PATH, austausch -> {
            try (austausch) {
                requests.add(new String(austausch.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                if (breaks) {
                    return;
                }
                if (!delay.isZero()) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                austausch.getResponseHeaders().set("Content-Type", contentType);
                austausch.sendResponseHeaders(status, body.length);
                austausch.getResponseBody().write(body);
            }
        });
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public String lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    public void responds(int status, String inhaltstyp, String body) {
        this.status = status;
        this.contentType = inhaltstyp;
        this.response = body;
    }

    public void hangs(Duration verzoegerung) {
        this.delay = verzoegerung;
    }

    public void breaks() {
        this.breaks = true;
    }

    public void reset() {
        requests.clear();
        delay = Duration.ZERO;
        breaks = false;
        responds(200, "application/json", DEFAULT_BEITRAG);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
