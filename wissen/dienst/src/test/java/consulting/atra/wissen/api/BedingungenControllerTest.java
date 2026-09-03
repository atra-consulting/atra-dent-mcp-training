package consulting.atra.wissen.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.model.embedding=none")
@AutoConfigureMockMvc
@Import(TestEmbedding.class)
class BedingungenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a question returns Fundstellen with every field of the contract")
    void searchBedingungen() throws Exception {
        String response = mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_B", "frage": "Wie lange ist die Wartezeit fuer Zahnersatz?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tarif").value("ATRA_DENT_B"))
                .andExpect(jsonPath("$.frage").value("Wie lange ist die Wartezeit fuer Zahnersatz?"))
                .andExpect(jsonPath("$.treffer").isArray())
                .andExpect(jsonPath("$.treffer[0].dokumentId").isNotEmpty())
                .andExpect(jsonPath("$.treffer[0].abschnittId").isNotEmpty())
                .andExpect(jsonPath("$.treffer[0].ueberschrift").isNotEmpty())
                .andExpect(jsonPath("$.treffer[0].text").isNotEmpty())
                .andExpect(jsonPath("$.treffer[0].bewertung").isNumber())
                .andExpect(jsonPath("$.treffer[0].pdfUrl").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        DocumentContext read = JsonPath.parse(response);
        List<String> documents = read.read("$.treffer[*].dokumentId");
        List<String> sections = read.read("$.treffer[*].abschnittId");
        List<String> htmlUrls = read.read("$.treffer[*].htmlUrl");
        List<Number> bewertungen = read.read("$.treffer[*].bewertung");

        assertThat(documents).isNotEmpty().hasSizeLessThanOrEqualTo(5);
        assertThat(documents).allSatisfy(dokument -> assertThat(dokument).isIn(
                "atra-dent-balance-avb", "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung"));
        List<String> fundstellen = new ArrayList<>();
        for (int position = 0; position < documents.size(); position++) {
            fundstellen.add(documents.get(position) + "#" + sections.get(position));
        }
        assertThat(fundstellen).doesNotHaveDuplicates();

        for (int position = 0; position < htmlUrls.size(); position++) {
            assertThat(htmlUrls.get(position)).isEqualTo(
                    "/dokumente/" + documents.get(position) + "/html#" + sections.get(position));
        }
        assertThat(bewertungen).allSatisfy(bewertung ->
                assertThat(bewertung.doubleValue()).isBetween(0.0, 1.0));
        assertThat(bewertungen).isSortedAccordingTo(
                (links, rechts) -> Double.compare(rechts.doubleValue(), links.doubleValue()));
    }

    @Test
    @DisplayName("anzahl limits the hit list")
    void searchBedingungenWithAnzahl() throws Exception {
        mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_X", "frage": "Was ist ein Implantat?", "anzahl": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treffer.length()").value(2));
    }

    @Test
    @DisplayName("an unknown Tarif in the body is 400 and names the four keys")
    void unknownTarif() throws Exception {
        mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_Z", "frage": "Wie lange ist die Wartezeit?"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unbekannter Tarif"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("https://atra.example/fehler/unbekannter-tarif"))
                .andExpect(jsonPath("$.instance").value("/api/v1/bedingungen/suche"))
                .andExpect(jsonPath("$.detail").value(containsString("ATRA_DENT_X_SB")));
    }

    @Test
    @DisplayName("without a Tarif the request is 400, because the answer would otherwise be arbitrary")
    void tarifMissing() throws Exception {
        mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"frage": "Wie lange ist die Wartezeit?"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Fehlerhafte Anfrage"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(containsString("tarif")));
    }

    @Test
    @DisplayName("without a question the request is 400")
    void questionMissing() throws Exception {
        mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_S"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("frage")));
    }

    @Test
    @DisplayName("a question that is too short and an anzahl that is too large are 400")
    void limitsOfTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_S", "frage": "ab"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("frage")));

        mockMvc.perform(post("/api/v1/bedingungen/suche")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tarif": "ATRA_DENT_S", "frage": "Wie lange ist die Wartezeit?", "anzahl": 99}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("anzahl")));
    }
}
