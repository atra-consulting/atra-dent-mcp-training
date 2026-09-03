package consulting.atra.wissen.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.ai.model.embedding=none")
@AutoConfigureMockMvc
@Import(TestEmbedding.class)
class DokumenteControllerTest {

    private static final String SMART = "$[?(@.dokumentId=='atra-dent-smart-avb')]";
    private static final String GOZ = "$[?(@.dokumentId=='atra-dent-goz-zuordnung')]";
    private static final String HANDBOOK = "$[?(@.dokumentId=='atra-dent-beratungshandbuch')]";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("without a filter all six documents are in the catalog")
    void listDocuments() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].dokumentId").value(containsInAnyOrder(
                        "atra-dent-smart-avb", "atra-dent-balance-avb", "atra-dent-brillant-avb",
                        "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung",
                        "atra-dent-beratungshandbuch")))
                .andExpect(jsonPath(SMART + ".art").value("BEDINGUNGSWERK"))
                .andExpect(jsonPath(SMART + ".tarif").value("ATRA_DENT_S"))
                .andExpect(jsonPath(SMART + ".titel").isNotEmpty())
                .andExpect(jsonPath(SMART + ".pdfUrl").value("/dokumente/atra-dent-smart-avb/pdf"))
                .andExpect(jsonPath(SMART + ".htmlUrl").value("/dokumente/atra-dent-smart-avb/html"))
                .andExpect(jsonPath(GOZ + ".art").value("GOZ_ZUORDNUNG"))
                .andExpect(jsonPath("$[*].tarif").value(containsInAnyOrder(
                        "ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X")));
    }

    @Test
    @DisplayName("every document names its Vertraulichkeit -- the required field is missing nowhere")
    void everyDocumentNamesItsStufe() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].vertraulichkeit").value(containsInAnyOrder(
                        "OEFFENTLICH", "OEFFENTLICH", "OEFFENTLICH",
                        "OEFFENTLICH", "OEFFENTLICH", "INTERN")))
                .andExpect(jsonPath(SMART + ".vertraulichkeit").value("OEFFENTLICH"))
                .andExpect(jsonPath(HANDBOOK + ".vertraulichkeit").value("INTERN"))
                .andExpect(jsonPath(HANDBOOK + ".art").value("BERATUNGSHANDBUCH"))
                .andExpect(jsonPath(HANDBOOK + ".tarif").doesNotExist());
    }

    @Test
    @DisplayName("with vertraulichkeit=OEFFENTLICH the Handbuch stays out")
    void listDocumentsPublicOnly() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente").param("vertraulichkeit", "OEFFENTLICH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[*].dokumentId").value(containsInAnyOrder(
                        "atra-dent-smart-avb", "atra-dent-balance-avb", "atra-dent-brillant-avb",
                        "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung")))
                .andExpect(jsonPath(HANDBOOK).doesNotExist());
    }

    @Test
    @DisplayName("with vertraulichkeit=INTERN exactly the Beratungshandbuch comes back")
    void listDocumentsInternalOnly() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente").param("vertraulichkeit", "INTERN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dokumentId").value("atra-dent-beratungshandbuch"))
                .andExpect(jsonPath("$[0].art").value("BERATUNGSHANDBUCH"))
                .andExpect(jsonPath("$[0].vertraulichkeit").value("INTERN"));
    }

    @Test
    @DisplayName("the Tarif filter includes the cross-Tarif documents and the Handbuch")
    void listDocumentsWithTarif() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente").param("tarif", "ATRA_DENT_S"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].dokumentId").value(containsInAnyOrder(
                        "atra-dent-smart-avb", "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung",
                        "atra-dent-beratungshandbuch")));
    }

    @Test
    @DisplayName("both filters together: the Tarif selects, the Vertraulichkeit cuts off")
    void listDocumentsWithTarifAndStufe() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente")
                        .param("tarif", "ATRA_DENT_B")
                        .param("vertraulichkeit", "OEFFENTLICH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].dokumentId").value(containsInAnyOrder(
                        "atra-dent-balance-avb", "atra-dent-tarifvergleich",
                        "atra-dent-goz-zuordnung")));

        mockMvc.perform(get("/api/v1/dokumente")
                        .param("tarif", "ATRA_DENT_B")
                        .param("vertraulichkeit", "INTERN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dokumentId").value("atra-dent-beratungshandbuch"));
    }

    @Test
    @DisplayName("the Handbuch is in the list for every Tarif")
    void theHandbuchAppliesToEveryTarif() throws Exception {
        for (String tarif : new String[]{"ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB"}) {
            mockMvc.perform(get("/api/v1/dokumente").param("tarif", tarif))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(HANDBOOK + ".dokumentId")
                            .value("atra-dent-beratungshandbuch"))
                    .andExpect(jsonPath(HANDBOOK + ".tarif").doesNotExist());
        }
    }

    @Test
    @DisplayName("the Selbstbehalt variant gets the Bedingungswerk of brillant")
    void listDocumentsForSelbstbehalt() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente")
                        .param("tarif", "ATRA_DENT_X_SB")
                        .param("vertraulichkeit", "OEFFENTLICH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].dokumentId").value(containsInAnyOrder(
                        "atra-dent-brillant-avb", "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung")))
                .andExpect(jsonPath("$[?(@.dokumentId=='atra-dent-brillant-avb')].tarif")
                        .value("ATRA_DENT_X"));
    }

    @Test
    @DisplayName("metadata of a single document")
    void fetchDocument() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente/atra-dent-balance-avb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dokumentId").value("atra-dent-balance-avb"))
                .andExpect(jsonPath("$.art").value("BEDINGUNGSWERK"))
                .andExpect(jsonPath("$.tarif").value("ATRA_DENT_B"))
                .andExpect(jsonPath("$.titel").value(
                        containsString("atra.dent.balance")));
    }

    @Test
    @DisplayName("the PDF comes as application/pdf, complete and inline")
    void fetchDocumentPdf() throws Exception {
        Path file = Path.of("..", "generated", "pdf", "atra-dent-balance-avb.pdf");

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/dokumente/atra-dent-balance-avb/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"atra-dent-balance-avb.pdf\""))
                .andReturn()
                .getResponse();

        byte[] read = response.getContentAsByteArray();
        assertThat(read).hasSize((int) Files.size(file));
        assertThat(read.length).isGreaterThan(1_000_000);
        assertThat(new String(read, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(read.length));
    }

    @Test
    @DisplayName("the HTML version comes with a charset and the section ids")
    void fetchDocumentHtml() throws Exception {
        String html = mockMvc.perform(get("/api/v1/dokumente/atra-dent-balance-avb/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("text/html;charset=UTF-8")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<section id=\"wartezeiten\">");
        assertThat(html).contains("<section id=\"anlage-a\">");
        assertThat(html).contains("§");
    }

    @Test
    @DisplayName("the internal Handbuch stays directly retrievable, as metadata, PDF and HTML")
    void theHandbuchStaysDirectlyRetrievable() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente/atra-dent-beratungshandbuch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dokumentId").value("atra-dent-beratungshandbuch"))
                .andExpect(jsonPath("$.art").value("BERATUNGSHANDBUCH"))
                .andExpect(jsonPath("$.vertraulichkeit").value("INTERN"))
                .andExpect(jsonPath("$.tarif").doesNotExist())
                .andExpect(jsonPath("$.titel").value(containsString("Beratungshandbuch")));

        MockHttpServletResponse pdf = mockMvc.perform(
                        get("/api/v1/dokumente/atra-dent-beratungshandbuch/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"atra-dent-beratungshandbuch.pdf\""))
                .andReturn()
                .getResponse();
        byte[] read = pdf.getContentAsByteArray();
        assertThat(read).hasSize((int) Files.size(
                Path.of("..", "generated", "pdf", "atra-dent-beratungshandbuch.pdf")));
        assertThat(new String(read, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        String html = mockMvc.perform(get("/api/v1/dokumente/atra-dent-beratungshandbuch/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("text/html;charset=UTF-8")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("<section id=\"einwand-zu-teuer\">");
        assertThat(html).contains("INTERN");
    }

    @Test
    @DisplayName("a Vertraulichkeit that does not exist is 400 and names value and parameter")
    void unknownVertraulichkeit() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente").param("vertraulichkeit", "GEHEIM"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(allOf(
                        containsString("GEHEIM"),
                        containsString("vertraulichkeit"))));
    }

    @Test
    @DisplayName("an unknown id is 404 with an error per RFC 7807")
    void unknownDocument() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente/gibt-es-nicht"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dokument nicht gefunden"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("https://atra.example/fehler/dokument-nicht-gefunden"))
                .andExpect(jsonPath("$.instance").value("/api/v1/dokumente/gibt-es-nicht"))
                .andExpect(jsonPath("$.detail").value(
                        containsString("atra-dent-balance-avb")));
    }

    @Test
    @DisplayName("PDF and HTML of an unknown document are 404 too")
    void unknownDocumentInEveryVariant() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente/gibt-es-nicht/pdf"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/v1/dokumente/gibt-es-nicht/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("an unknown Tarif in the filter is 400 and names the four keys")
    void unknownTarifInTheFilter() throws Exception {
        mockMvc.perform(get("/api/v1/dokumente").param("tarif", "ATRA_DENT_Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unbekannter Tarif"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(allOf(
                        containsString("ATRA_DENT_Z"),
                        containsString("ATRA_DENT_S"),
                        containsString("ATRA_DENT_B"),
                        containsString("ATRA_DENT_X"),
                        containsString("ATRA_DENT_X_SB"))));
    }
}
