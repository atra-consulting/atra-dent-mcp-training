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
class ErstattungsberechnungControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void calculates_the_erstattung_and_reports_the_step_sequence() {
        assertThat(mvc.post().uri("/api/v1/erstattungsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tarifId": "ATRA_DENT_B",
                          "versicherungsbeginn": "2022-03-01",
                          "behandlungsdatum": "2022-05-01",
                          "positionen": [
                            {"goz": "2210", "leistungsbereich": "ZE", "betrag": "1000.00",
                             "beschreibung": "Krone Regio 36"}
                          ],
                          "gkvLeistung": "300.00"
                        }
                        """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.erstattungsbetrag").isEqualTo("450.00");
    }

    @Test
    void returns_rechnungsbetrag_eigenanteil_versicherungsjahr_and_six_steps() {
        var result = mvc.post().uri("/api/v1/erstattungsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tarifId": "ATRA_DENT_B",
                          "versicherungsbeginn": "2022-03-01",
                          "behandlungsdatum": "2022-05-01",
                          "positionen": [
                            {"goz": "2210", "leistungsbereich": "ZE", "betrag": "1000.00",
                             "beschreibung": "Krone Regio 36"}
                          ],
                          "gkvLeistung": "300.00"
                        }
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.rechnungsbetrag").isEqualTo("1000.00");
        assertThat(result).bodyJson().extractingPath("$.eigenanteil").isEqualTo("250.00");
        assertThat(result).bodyJson().extractingPath("$.versicherungsjahr").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.schritte.length()").isEqualTo(6);
    }

    @Test
    void a_behandlungsdatum_before_versicherungsbeginn_is_a_400_as_problem_json() {
        assertThat(mvc.post().uri("/api/v1/erstattungsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tarifId": "ATRA_DENT_B",
                          "versicherungsbeginn": "2022-03-01",
                          "behandlungsdatum": "2021-01-01",
                          "positionen": [
                            {"goz": "2210", "leistungsbereich": "ZE", "betrag": "1000.00",
                             "beschreibung": "Krone Regio 36"}
                          ]
                        }
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void empty_positionen_are_a_400() {
        assertThat(mvc.post().uri("/api/v1/erstattungsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tarifId": "ATRA_DENT_B",
                          "versicherungsbeginn": "2022-03-01",
                          "behandlungsdatum": "2022-05-01",
                          "positionen": []
                        }
                        """))
                .hasStatus(400);
    }

    @Test
    void an_unknown_leistungsbereich_key_in_the_vorverbrauch_is_a_400_as_problem_json() {
        assertThat(mvc.post().uri("/api/v1/erstattungsberechnung")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tarifId": "ATRA_DENT_B",
                          "versicherungsbeginn": "2022-03-01",
                          "behandlungsdatum": "2022-05-01",
                          "positionen": [
                            {"goz": "2210", "leistungsbereich": "ZE", "betrag": "1000.00",
                             "beschreibung": "Krone Regio 36"}
                          ],
                          "verbrauch": {
                            "leistungsbereiche": { "Zahnersatz": "450.00" }
                          }
                        }
                        """))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail").asString()
                .contains("Zahnersatz")
                .contains("ZE");
    }
}
