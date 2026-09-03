package consulting.atra.rechenkern.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class BeitragsberechnungControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void calculates_the_beitrag_and_returns_it_as_a_decimal_string() {
        assertThat(mvc.post().uri("/api/v1/beitragsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"geburtsdatum":"1990-04-12","tarifId":"ATRA_DENT_B","gewuenschterBeginn":"2026-01-01"}
                        """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.monatsbeitrag").isEqualTo("20.90");
    }

    @Test
    void an_eintrittsalter_that_is_too_high_is_a_400_as_problem_json() {
        assertThat(mvc.post().uri("/api/v1/beitragsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"geburtsdatum":"1950-04-12","tarifId":"ATRA_DENT_X","gewuenschterBeginn":"2026-01-01"}
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void a_missing_required_field_is_a_400() {
        assertThat(mvc.post().uri("/api/v1/beitragsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tarifId":"ATRA_DENT_B","gewuenschterBeginn":"2026-01-01"}
                        """))
                .hasStatus(400);
    }
}
