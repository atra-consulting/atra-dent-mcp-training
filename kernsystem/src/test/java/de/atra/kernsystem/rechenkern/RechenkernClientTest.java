package de.atra.kernsystem.rechenkern;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.TarifId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RechenkernClientTest {

    private static final LocalDate DATE_OF_BIRTH = LocalDate.of(1990, 4, 12);
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private RechenkernStub rechenkern;
    private RechenkernClient client;

    @BeforeEach
    void start() {
        rechenkern = new RechenkernStub();
        client = RechenkernClient.forUrl(rechenkern.url(), Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        rechenkern.close();
    }

    @Test
    @DisplayName("the Beitrag arrives as a decimal string and stays exact")
    void beitragIsRead() {
        rechenkern.responds(200, "application/json", "{\"monatsbeitrag\":\"20.90\"}");

        assertThat(client.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_B, START))
                .isEqualByComparingTo("20.90")
                .satisfies(betrag -> assertThat(betrag.scale()).isEqualTo(2));

        assertThat(rechenkern.lastRequest())
                .contains("\"geburtsdatum\":\"1990-04-12\"")
                .contains("\"tarifId\":\"ATRA_DENT_B\"")
                .contains("\"gewuenschterBeginn\":\"2026-01-01\"");
    }

    @Test
    @DisplayName("a 400 is a domain refusal and comes with the Rechenkern's Begruendung")
    void fourHundredIsADomainError() {
        rechenkern.responds(400, "application/problem+json", """
                {"type":"https://atra.example/fehler/fehlerhafte-anfrage",
                 "title":"Fehlerhafte Anfrage","status":400,
                 "detail":"Eintrittsalter 86 liegt ausserhalb der Grenzen von ATRA_DENT_X (18 bis 65)"}""");

        assertThatThrownBy(() -> client.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_X, START))
                .isInstanceOf(DomainException.class)
                .hasMessage("Eintrittsalter 86 liegt ausserhalb der Grenzen "
                        + "von ATRA_DENT_X (18 bis 65)");
    }

    @Test
    @DisplayName("even a 400 without a detail stays a domain error and does not become a failure")
    void fourHundredWithoutDetail() {
        rechenkern.responds(400, "application/problem+json", "{\"status\":400}");

        assertThatThrownBy(() -> client.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_X, START))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("nicht berechnen");
    }

    @Test
    @DisplayName("a 5xx is a failure and not a domain answer")
    void fiveHundredIsAFailure() {
        rechenkern.responds(503, "application/problem+json", "{\"status\":503}");

        assertThatThrownBy(() -> client.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_B, START))
                .isInstanceOf(RechenkernUnreachableException.class)
                .hasMessageContaining("Stoerung");
    }

    @Test
    @DisplayName("a Rechenkern that is not running at all is a failure")
    void unreachableIsAFailure() throws Exception {
        int freierPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freierPort = socket.getLocalPort();
        }
        var withoutNeighbours = RechenkernClient.forUrl("http://localhost:" + freierPort,
                Duration.ofSeconds(2));

        assertThatThrownBy(() -> withoutNeighbours.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_B, START))
                .isInstanceOf(RechenkernUnreachableException.class)
                .hasMessageContaining("nicht erreichbar");
    }

    @Test
    @DisplayName("a timeout is treated like an outage")
    void aTimeoutIsAFailure() {
        rechenkern.hangs(Duration.ofSeconds(5));
        var impatient = RechenkernClient.forUrl(rechenkern.url(), Duration.ofMillis(200));

        assertThatThrownBy(() -> impatient.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_B, START))
                .isInstanceOf(RechenkernUnreachableException.class);
    }

    @Test
    @DisplayName("a response without a monatsbeitrag is a failure and not a zero")
    void responseWithoutBetrag() {
        rechenkern.responds(200, "application/json", "{\"ergebnis\":\"kommt gleich\"}");

        assertThatThrownBy(() -> client.monthlyBeitrag(DATE_OF_BIRTH, TarifId.ATRA_DENT_B, START))
                .isInstanceOf(RechenkernUnreachableException.class)
                .hasMessageContaining("monatsbeitrag");
    }

    @Test
    @DisplayName("without a configured URL the service fails at startup, not on the first request")
    void withoutAUrlNoStart() {
        assertThatThrownBy(() -> new RechenkernProperties(null, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kernsystem.rechenkern.url");
    }
}
