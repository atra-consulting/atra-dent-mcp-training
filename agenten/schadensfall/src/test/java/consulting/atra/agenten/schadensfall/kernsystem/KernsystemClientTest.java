package consulting.atra.agenten.schadensfall.kernsystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KernsystemClientTest {

    private static final String BASIS = "http://kernsystem.test";
    private static final String API_KEY = "atra-lab-2026";

    private MockRestServiceServer server;
    private KernsystemClient client;

    @BeforeEach
    void prepare() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KernsystemClient(new KernsystemProperties(BASIS, API_KEY), builder);
    }

    @Test
    @DisplayName("schadensfaelleWithStatus asks comma-separated and with the API key")
    void schadensfaelleWithStatusAsksCommaSeparated() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle?status=eingereicht,in_pruefung"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(KernsystemClient.API_KEY_HEADER, API_KEY))
                .andRespond(withSuccess("""
                        [{"id": 50071, "status": "eingereicht", "kundenId": 10001},
                         {"id": 50014, "status": "in_pruefung", "kundenId": 10002}]
                        """, MediaType.APPLICATION_JSON));

        List<JsonNode> cases = client.casesWithStatus("eingereicht", "in_pruefung");

        assertThat(cases).hasSize(2);
        assertThat(cases.getFirst().get("id").asLong()).isEqualTo(50071L);
        assertThat(cases.getLast().get("status").asString()).isEqualTo("in_pruefung");
        server.verify();
    }

    @Test
    @DisplayName("an empty list is no special case")
    void anEmptyList() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle?status=eingereicht"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.casesWithStatus("eingereicht")).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("schadensfall reads the single Schadensfall")
    void readsTheSchadensfall() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/50071"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(KernsystemClient.API_KEY_HEADER, API_KEY))
                .andRespond(withSuccess("{\"id\": 50071, \"kundenId\": 10001}",
                        MediaType.APPLICATION_JSON));

        Optional<JsonNode> fall = client.fall(50071);

        assertThat(fall).isPresent();
        assertThat(fall.get().get("kundenId").asLong()).isEqualTo(10001L);
        server.verify();
    }

    @Test
    @DisplayName("an unknown Schadensfall is empty and no error")
    void aSchadensfallWithoutHitsIsEmpty() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/99999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Nicht gefunden\"}"));

        assertThat(client.fall(99999)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("claiming sets in_pruefung with the expected status eingereicht")
    void claimingSetsInPruefung() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/50071"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(KernsystemClient.API_KEY_HEADER, API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("in_pruefung"))
                .andExpect(jsonPath("$.erwarteterStatus").value("eingereicht"))
                .andRespond(withSuccess("{\"id\": 50071, \"status\": \"in_pruefung\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.claim(50071)).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("a 409 while claiming means someone else was faster -- false, no exception")
    void claimingOnAConflictIsFalse() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/50071"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Konflikt\"}"));

        assertThat(client.claim(50071)).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("a 500 while claiming is a failure and propagates")
    void claimingOnAServerErrorThrows() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/50071"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.claim(50071))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    @Test
    @DisplayName("resetting restores eingereicht")
    void resettingRestoresEingereicht() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/50071"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.status").value("eingereicht"))
                .andExpect(jsonPath("$.erwarteterStatus").value("in_pruefung"))
                .andRespond(withSuccess("{\"id\": 50071, \"status\": \"eingereicht\"}",
                        MediaType.APPLICATION_JSON));

        client.reset(50071);
        server.verify();
    }

    @Test
    @DisplayName("a 409 while resetting is swallowed -- the Schadensfall has already moved on")
    void resettingOnAConflictIsSilent() {
        server.expect(requestTo(BASIS + "/api/v1/schadensfaelle/50071"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"title\":\"Konflikt\"}"));

        client.reset(50071);
        server.verify();
    }
}
