package de.atra.kernsystem.rechenkern;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.TarifId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

@Service
public class RechenkernClient {

    private static final Logger log = LoggerFactory.getLogger(RechenkernClient.class);

    private static final String PATH = "/api/v1/beitragsberechnung";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private static final HttpClient BEITRAGS_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final URI target;
    private final Duration requestTimeout;

    public RechenkernClient(RechenkernProperties settings) {
        Objects.requireNonNull(settings, "einstellungen");
        this.target = URI.create(settings.url() + PATH);
        this.requestTimeout = settings.timeout();
    }

    static RechenkernClient forUrl(String baseUrl, Duration timeout) {
        return new RechenkernClient(new RechenkernProperties(baseUrl, timeout));
    }

    public BigDecimal monthlyBeitrag(LocalDate geburtsdatum, TarifId tarifId,
                                    LocalDate gewuenschterBeginn) {

        Objects.requireNonNull(geburtsdatum, "geburtsdatum");
        Objects.requireNonNull(tarifId, "tarifId");
        Objects.requireNonNull(gewuenschterBeginn, "gewuenschterBeginn");

        String body = MAPPER.writeValueAsString(Map.of(
                "geburtsdatum", geburtsdatum.toString(),
                "tarifId", tarifId.getValue(),
                "gewuenschterBeginn", gewuenschterBeginn.toString()));

        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, application/problem+json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = BEITRAGS_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure("Die Beitragsberechnung wurde unterbrochen", interrupted);
        } catch (IOException unreachable) {
            throw failure("Der Rechenkern unter " + target + " ist nicht erreichbar",
                    unreachable);
        }

        if (response.statusCode() == 200) {
            return readBeitrag(response.body());
        }
        if (response.statusCode() == 400) {
            throw new DomainException(reasonFrom(response.body()));
        }
        log.warn("Rechenkern antwortet mit HTTP {} auf die Beitragsberechnung: {}",
                response.statusCode(), response.body());
        throw failure("Der Rechenkern meldet eine Stoerung (HTTP "
                + response.statusCode() + ")", null);
    }

    private BigDecimal readBeitrag(String body) {
        JsonNode monatsbeitrag;
        try {
            monatsbeitrag = MAPPER.readTree(body).path("monatsbeitrag");
        } catch (JacksonException unreadable) {
            throw failure("Der Rechenkern hat keine lesbare Antwort geliefert", unreadable);
        }
        if (monatsbeitrag.isMissingNode() || monatsbeitrag.isNull()) {
            throw failure("Die Antwort des Rechenkerns enthaelt keinen monatsbeitrag", null);
        }
        try {
            return new BigDecimal(monatsbeitrag.asString().strip());
        } catch (NumberFormatException notABetrag) {
            throw failure("Der Rechenkern hat '" + monatsbeitrag.asString()
                    + "' als Monatsbeitrag geliefert", notABetrag);
        }
    }

    private static String reasonFrom(String body) {
        try {
            String detail = MAPPER.readTree(body).path("detail").asString();
            if (!detail.isBlank()) {
                return detail;
            }
        } catch (JacksonException unreadable) {
            log.warn("Der Rechenkern hat einen unlesbaren Fehlerrumpf geliefert: {}",
                    unreadable.getMessage());
        }
        return "Der Beitrag laesst sich fuer diese Angaben nicht berechnen";
    }

    private static RechenkernUnreachableException failure(String message, Throwable cause) {
        if (cause != null) {
            log.warn("Beitragsberechnung beim Rechenkern gescheitert: {}", message, cause);
        }
        return new RechenkernUnreachableException(message
                + ". Das ist eine technische Stoerung und keine Auskunft ueber den Tarif: "
                + "Der Beitrag laesst sich gerade nicht ermitteln und sollte spaeter erneut "
                + "erfragt werden. Das Kernsystem rechnet ihn nicht ersatzweise selbst aus, "
                + "weil eine zweite Rechnung eine andere Zahl liefern koennte als die "
                + "verbindliche.");
    }
}
