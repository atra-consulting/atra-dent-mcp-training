package consulting.atra.agenten.schadensfall.kernsystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class KernsystemClient {

    public static final String API_KEY_HEADER = "x-api-key";

    static final String PATH = "/api/v1/schadensfaelle";

    private static final Logger log = LoggerFactory.getLogger(KernsystemClient.class);

    private static final String STATUS = "status";
    private static final String EXPECTED_STATUS = "erwarteterStatus";
    private static final String EINGEREICHT = "eingereicht";
    private static final String IN_PRUEFUNG = "in_pruefung";

    private final RestClient rest;

    public KernsystemClient(KernsystemProperties properties, RestClient.Builder erbauer) {
        this.rest = erbauer
                .baseUrl(properties.url())
                .defaultHeader(API_KEY_HEADER, properties.apiKey())
                .build();
    }

    public List<JsonNode> casesWithStatus(String... status) {
        JsonNode answer = rest.get()
                .uri(bauer -> bauer.path(PATH)
                        .queryParam(STATUS, String.join(",", status))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        if (answer == null || !answer.isArray()) {
            return List.of();
        }
        List<JsonNode> cases = new ArrayList<>();
        answer.forEach(cases::add);
        return List.copyOf(cases);
    }

    public Optional<JsonNode> fall(long id) {
        try {
            return Optional.ofNullable(rest.get()
                    .uri(PATH + "/{id}", id)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw error;
        }
    }

    public boolean claim(long id) {
        try {
            change(id, Map.of(STATUS, IN_PRUEFUNG, EXPECTED_STATUS, EINGEREICHT));
            return true;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode() == HttpStatus.CONFLICT) {
                log.debug("Fall {} war schon vergeben - ein anderer war schneller", id);
                return false;
            }
            throw error;
        }
    }

    public void reset(long id) {
        try {
            change(id, Map.of(STATUS, EINGEREICHT, EXPECTED_STATUS, IN_PRUEFUNG));
            log.info("Fall {} zurueckgesetzt auf {} - die Pruefung war steckengeblieben",
                    id, EINGEREICHT);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode() == HttpStatus.CONFLICT) {
                log.info("Fall {} nicht zurueckgesetzt: Er steht nicht mehr auf {}",
                        id, IN_PRUEFUNG);
                return;
            }
            throw error;
        }
    }

    private void change(long id, Map<String, String> aenderung) {
        rest.patch()
                .uri(PATH + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(aenderung)
                .retrieve()
                .toBodilessEntity();
    }
}
