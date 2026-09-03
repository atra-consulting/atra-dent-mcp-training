package consulting.atra.wissen.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.model.embedding=none")
@AutoConfigureMockMvc
@Import(TestEmbedding.class)
class GozControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a Rechnung with all four Zustaende, in the order of the request")
    void checkGoz() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_B",
                                 "nummern": ["9010", "6030", "0010", "9999"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tarif").value("ATRA_DENT_B"))
                .andExpect(jsonPath("$.befunde.length()").value(4))

                .andExpect(jsonPath("$.befunde[0].nummer").value("9010"))
                .andExpect(jsonPath("$.befunde[0].status").value("ENTHALTEN"))
                .andExpect(jsonPath("$.befunde[0].leistungsbereich").value("IMP"))
                .andExpect(jsonPath("$.befunde[0].abschnitt").value("K"))
                .andExpect(jsonPath("$.befunde[0].quote").value(85))
                .andExpect(jsonPath("$.befunde[0].grenzen.maxFaelle").value(4))
                .andExpect(jsonPath("$.befunde[0].grenzen.zeitraumJahre").value(5))
                .andExpect(jsonPath("$.befunde[0].bezeichnung").isNotEmpty())
                .andExpect(jsonPath("$.befunde[0].begruendung").isNotEmpty())

                .andExpect(jsonPath("$.befunde[1].nummer").value("6030"))
                .andExpect(jsonPath("$.befunde[1].status").value("ENTHALTEN"))
                .andExpect(jsonPath("$.befunde[1].leistungsbereich").value("KFO"))
                .andExpect(jsonPath("$.befunde[1].quote").value(60))
                .andExpect(jsonPath("$.befunde[1].grenzen.limitGesamt").value("1200.00"))
                .andExpect(jsonPath("$.befunde[1].grenzen.wartezeitMonate").value(8))

                .andExpect(jsonPath("$.befunde[2].nummer").value("0010"))
                .andExpect(jsonPath("$.befunde[2].status").value("NICHT_BESTIMMBAR"))
                .andExpect(jsonPath("$.befunde[2].leistungsbereich").doesNotExist())
                .andExpect(jsonPath("$.befunde[2].quote").doesNotExist())
                .andExpect(jsonPath("$.befunde[2].grenzen").doesNotExist())
                .andExpect(jsonPath("$.befunde[2].bezeichnung").isNotEmpty())
                .andExpect(jsonPath("$.befunde[2].begruendung").value(containsString("klären")))

                .andExpect(jsonPath("$.befunde[3].nummer").value("9999"))
                .andExpect(jsonPath("$.befunde[3].status").value("UNBEKANNT"))
                .andExpect(jsonPath("$.befunde[3].bezeichnung").doesNotExist())
                .andExpect(jsonPath("$.befunde[3].abschnitt").doesNotExist())
                .andExpect(jsonPath("$.befunde[3].begruendung").isNotEmpty());
    }

    @Test
    @DisplayName("an uninsured Leistungsbereich is a clear refusal without a Quote")
    void notIncluded() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_S", "nummern": ["9010"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.befunde[0].status").value("NICHT_ENTHALTEN"))
                .andExpect(jsonPath("$.befunde[0].leistungsbereich").value("IMP"))
                .andExpect(jsonPath("$.befunde[0].quote").doesNotExist())
                .andExpect(jsonPath("$.befunde[0].grenzen").doesNotExist());
    }

    @Test
    @DisplayName("without a Leistungsbereich-specific limit there is an empty object, not null")
    void limitsWithoutAnEntry() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_B", "nummern": ["2200"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.befunde[0].status").value("ENTHALTEN"))
                .andExpect(jsonPath("$.befunde[0].leistungsbereich").value("ZE"))
                .andExpect(jsonPath("$.befunde[0].grenzen").isMap())
                .andExpect(jsonPath("$.befunde[0].grenzen.length()").value(0));
    }

    @Test
    @DisplayName("duplicate Nummern are answered individually")
    void duplicateNummern() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_X", "nummern": ["1040", "1040", "1040"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.befunde.length()").value(3))
                .andExpect(jsonPath("$.befunde[2].nummer").value("1040"))
                .andExpect(jsonPath("$.befunde[2].status").value("ENTHALTEN"));
    }

    @Test
    @DisplayName("an unknown Tarif is 400 and names the four keys")
    void unknownTarif() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_ZZ", "nummern": ["9010"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unbekannter Tarif"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("https://atra.example/fehler/unbekannter-tarif"))
                .andExpect(jsonPath("$.detail").value(containsString("ATRA_DENT_X_SB")));
    }

    @Test
    @DisplayName("a Gebuehrennummer that is none is 400 and names the field")
    void invalidGebuehrennummer() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_B", "nummern": ["9010", "22a0"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Fehlerhafte Anfrage"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/goz/pruefung"))
                .andExpect(jsonPath("$.detail").value(containsString("nummern")));
    }

    @Test
    @DisplayName("without Nummern there is nothing to check: 400")
    void withoutNummern() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_B", "nummern": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("nummern")));
    }

    @Test
    @DisplayName("broken JSON is 400 with an error per RFC 7807")
    void brokenBody() throws Exception {
        mockMvc.perform(post("/api/v1/goz/pruefung")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{das ist kein JSON"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Fehlerhafte Anfrage"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }
}
