package de.atra.kernsystem.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class RechnungsextraktionControllerTest {

    static final String API_KEY = "atra-lab-2026";
    static final Path PDF = Path.of("..", "submissions", "rechnungen", "pdf", "mueller-fuellung.pdf");

    @Autowired
    MockMvcTester mvc;

    @Test
    void extracts_a_pdfa_rechnung() throws IOException {
        assertThat(mvc.post().uri("/api/v1/rechnungsextraktion")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_PDF)
                .content(Files.readAllBytes(PDF)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.rechnungsnummer").isEqualTo("2026-04892");
    }

    @Test
    void kein_pdfa_ist_ein_400_mit_generischer_meldung_als_problem_json() {
        assertThat(mvc.post().uri("/api/v1/rechnungsextraktion")
                .header("x-api-key", API_KEY)
                .contentType(MediaType.APPLICATION_PDF)
                .content("kein PDF".getBytes()))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("Das Dokument konnte nicht verarbeitet werden");
    }

    @Test
    void ohne_schluessel_kommt_401() {
        assertThat(mvc.post().uri("/api/v1/rechnungsextraktion")
                .contentType(MediaType.APPLICATION_PDF)
                .content(new byte[0]))
                .hasStatus(401);
    }
}
